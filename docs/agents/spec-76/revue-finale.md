# Revue finale — spec #76 (depuis `5163728`, opus / medium, 2026-11-07)

Chemins relatifs à `services/ingestion-service/src/main/java/fr/michael/logistics/ingestion/`.

## Lot B (à faire en premier) : structure et route

### B1 (point 4, moyen) — validation et cas d'usage de l'import
- `infrastructure/batch/ScanRowProcessor.java` l. 40-97 : les règles de `ScanRequest` (taille, blancs, `reasonShortEnough`, `reasonAllowed`) sont réécrites à la main, sans Bean Validation : la règle « toute donnée externe validée par Bean Validation avant `application` » n'est pas tenue, et les deux validations dériveront. Le contrôleur appelle `infrastructure/batch` directement, sans cas d'usage dans `application`.
- Attendu : chaque ligne validée par le `Validator` sur un DTO annoté (partagé avec `ScanRequest`, ou construit avec les mêmes contraintes) ; l'import passe par un service d'`application` (port d'import) que l'adaptateur `batch` implémente. ArchUnit reste vert (`org.springframework.batch` seulement dans `infrastructure.batch`).

### B2 (point 3, moyen) — requête qui n'est pas en multipart
- `infrastructure/web/ScanImportController.java` l. 27 : un corps JSON ou vide lève `MultipartException`, que `ApiExceptionHandler` ne traite pas : 500 au lieu de 400.
- Attendu : `@ExceptionHandler(MultipartException.class)` → 400 `problem+json` `file: required part is missing` ; le 413 de `MaxUploadSizeExceededException` (sous-classe) reste. Test sans multipart (vrai serveur si le 500 passe par `/error`, voir `environnement.md`).

## Lot A : lecture du CSV et journal (après le lot B)

### A1 (point 1, moyen) — lignes commençant par `#`
- `infrastructure/batch/ScanCsvImporter.java` l. 136-139 : `FlatFileItemReader` traite par défaut une ligne commençant par `#` comme un commentaire : elle disparaît sans être publiée, rejetée ni comptée, alors que `CsvFile.dataLines()` la compte dans le plafond.
- Attendu : `reader.setComments(new String[0])`, et un test avec une `shipmentReference` commençant par `#`.

### A2 (point 2, moyen) — champ entre guillemets sur plusieurs lignes
- `ScanCsvImporter.java` l. 136 et `infrastructure/batch/CsvFile.java` l. 31 et 55 : la spec annonce les guillemets RFC 4180, mais `SimpleRecordSeparatorPolicy` et `String.lines()` coupent un champ entre guillemets qui contient un saut de ligne (deux lignes rejetées, plafond et numéros de ligne faussés).
- Attendu : accepter ces champs (`DefaultRecordSeparatorPolicy`), avec un comptage des enregistrements cohérent dans `dataLines()` ; `line` des erreurs = première ligne physique de l'enregistrement. Test.

### A3 (point 7, faible) — espaces autour des cellules
- `CsvFile.java` l. 61 et `ScanCsvImporter.java` l. 164 : l'en-tête est rogné (`trim`), pas les cellules : ` PICKED_UP` est rejeté alors que ` type ` est accepté.
- Attendu (choix de l'orchestrateur, une seule règle) : rogner aussi les cellules hors guillemets, documenté dans le README. Test.

### A4 (point 5, faible) — journal d'un import en échec
- `ScanCsvImporter.java` l. 98-100 : `scan import finished` n'est écrit qu'en cas de succès ; un import interrompu (503) ou un job en échec ne laisse aucune ligne.
- Attendu : une ligne par import, succès ou échec, avec `published` et un champ `outcome` (champs JSON de premier niveau sous `gcp`). Mettre à jour `architecture.md` (section Import CSV). Test.

### A5 (point 6, faible) — interruption pendant l'attente du sémaphore
- `ScanCsvImporter.java` l. 116-121 : un thread interrompu pendant l'attente devient `ImportBusyException` (429 + `Retry-After`) alors que l'instance s'arrête.
- Attendu : 503 (`ImportInterruptedException` ou exception dédiée), flag d'interruption rétabli. Test.

# Seconde revue — `5163728..HEAD` (opus / medium, 2026-11-07, après les lots A et B)

Le point 8 de la revue (collision de `DeterministicEventId` par `|`) est écarté : le site vient du jeton, `type` est une énumération, `occurredAt` une date ISO ; deux lignes distinctes ne peuvent pas donner le même texte joint.

## Lot C : découpage du CSV (avant le lot D)

### C1 (point 2, moyen) — coût quadratique d'un guillemet jamais fermé
- `infrastructure/batch/CsvFile.java` l. 64-74, `infrastructure/batch/QuotedRecordSeparatorPolicy.java` l. 12 : à chaque ligne physique, `records()` refait `current.toString()` et recompte tous les guillemets du tampon ; un `"` suivi d'~1 M de sauts de ligne (2 Mo) coûte un temps quadratique, avant le sémaphore et le plafond de lignes.
- Attendu : parité des guillemets suivie ligne par ligne, sans recompter le tampon (idem dans la politique du lecteur). Test sur un tel fichier, en temps borné.

### C2 (point 3, moyen) — guillemet au milieu d'une cellule non guillemetée
- `QuotedRecordSeparatorPolicy.java` l. 11-13, `CsvFile.java` l. 70 : la parité compte tous les `"`, alors que `CsvCells` traite un `"` au milieu d'une cellule non guillemetée comme un caractère ordinaire ; `REF"7,PICKED_UP,…` colle les lignes suivantes (400 « quoted field is not closed » ou lignes fusionnées).
- Attendu : une seule règle pour `CsvFile`, la politique et `CsvCells` (choix de l'orchestrateur : seuls comptent les guillemets d'une cellule ouverte par `"`, comme RFC 4180 ; un `"` au milieu d'une cellule non guillemetée reste un caractère ordinaire). README à jour. Test.

### C3 (point 7, faible) — blancs avant un guillemet ouvrant
- `infrastructure/batch/CsvCells.java` l. 21-24 : seul `' '` est sauté avant le guillemet ouvrant, alors que les cellules non guillemetées sont rognées par `strip()` ; `\t"Paris, Nord"` est coupé à la virgule.
- Attendu : sauter les mêmes blancs que `strip()`. Test.

### C4 (point 9, faible) — fichier découpé et copié plusieurs fois
- `CsvFile.java` l. 30 et 54, `ScanCsvImporter.java` l. 161 : découpage en enregistrements fait deux fois (`parse`, `dataLines`), puis texte réencodé en octets pour le lecteur (~4 à 5 fois le fichier en mémoire).
- Attendu : nombre d'enregistrements calculé une fois dans `parse()` ; le lecteur reçoit les octets d'origine sans BOM (ou le texte sans réencodage).

## Lot D : rejet des lignes et publication (après le lot C)

### D1 (point 1, moyen) — référence ou site fait d'espaces insécables
- `infrastructure/batch/ScanRowProcessor.java` l. 70-89, `ScanCsvImporter.java` l. 210-211 : une `shipmentReference` en U+00A0 (ou U+2000–U+200A, U+FEFF) passe `@NotBlank`, `ScanEvent` lève `IllegalArgumentException`, non sautée : tout le job échoue, 500 sans `published` alors que des lots sont déjà publiés. Plus largement, tout échec du job autre que la publication rend ce 500.
- Attendu : `shipmentReference` et `site` contrôlés avec le motif de blancs de `ScanEvent` (comme `reason`), et l'`IllegalArgumentException` de `ScanEvent` convertie en ligne rejetée. Un échec imprévu du job après publication partielle rend une erreur qui porte `published`. Test avec une référence en espace insécable.

### D2 (point 4, moyen) — producteur Kafka sans délais bornés
- `infrastructure/messaging/KafkaScanPublisher.java` l. 50-52 : `send` peut bloquer `max.block.ms` (60 s) et `flush()` `delivery.timeout.ms` (120 s) : le délai d'accusé de 5 s ne sert à rien, Kafka en panne garde le sémaphore plusieurs minutes.
- Attendu (choix de l'orchestrateur, configurables dans `application.properties`, valent aussi pour `POST /api/scans`) : `max.block.ms` = 5 s, `request.timeout.ms` = 5 s, `delivery.timeout.ms` = 10 s ; ou une échéance globale sans `flush()`. Kafka en panne → 503 en ~10 s au plus par lot. `architecture.md` à jour. Test (Kafka arrêté ou injoignable, durée bornée).

### D3 (point 5, faible) — toute exception présentée comme « Kafka indisponible »
- `KafkaScanPublisher.java` l. 59 : `catch (… | RuntimeException e)` transforme une erreur de sérialisation ou un bogue en 503.
- Attendu : seules les erreurs de transport (`KafkaException`, échec d'accusé, délai) deviennent `ScanPublicationException` ; les autres remontent.

### D4 (point 6, faible) — `eventId` en forme courte
- `ScanRowProcessor.java` l. 65 : `UUID.fromString` accepte `1-1-1-1-1`, refusé par Jackson sur `POST /api/scans`.
- Attendu : forme canonique de 36 caractères exigée avant `fromString`. Test.

### D5 (relevé au lot C, faible) — code mort de la ligne vide
- `ScanRow.isBlank()` et la sentinelle de ligne vide ne servent plus : depuis C4, les enregistrements vides sont sautés à la lecture (`CsvFile.dataRecords()`).
- Attendu : les retirer, avec leurs tests devenus sans objet.
