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
