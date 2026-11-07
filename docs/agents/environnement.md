# Environnement des agents — Logistics Flow Platform

Lu par l'orchestrateur, les implémenteurs et `/preparer-spec` (skills globaux `~/.claude/skills/orchestrer-spec` et `preparer-spec`). Seulement ce qui est propre à ce projet.

## Vérifications
- `JAVA_HOME` (openjdk@21) est fixé pour toute session Claude Code du projet par `.claude/settings.local.json` (`env`, non commité, #44) : `./mvnw` tourne en Java 21 sans rien d'autre. Agent en worktree isolé : **jamais `source`** (refusé par le garde-fou de Claude Code). Dans le repo principal, `source scripts/env.sh` reste possible (il charge aussi `JWT_PRIVATE_KEY`, inutile aux tests).
- Rapides (après chaque fusion) : `./mvnw -B -ntp -q verify` dans chaque service touché par le ticket (`services/<service>`). Un test : `./mvnw -B -ntp test -Dtest=<Classe>`.
- Tester un statut d'erreur rendu par `/error` (400 de Spring MVC, 413, 500) : MockMvc ne fait pas le dispatch ERROR, il faut un vrai serveur (`webEnvironment = RANDOM_PORT`, `@LocalServerPort` de `org.springframework.boot.test.web.server`, `java.net.http.HttpClient` ; pas de `TestRestTemplate` sans dépendance en plus). Exemple : `ErrorDispatchTest` (ingestion-service, ai-service).
- shipment-service (#38) : `ApiExceptionHandler` rend en `ProblemDetail` les 400 de Bean Validation (`detail` = `champ: message`, ex. `expectedDeliveryAt: must not be null`) et de JSON illisible (`Invalid value for <champ>`, ou `Malformed request body`) : MockMvc les voit, pas besoin de vrai serveur. `ShipmentApiTest.DUE` : une `expectedDeliveryAt` valide (dans 3 jours) pour créer une expédition dans un test.
- Simuler ai-service arrêté (connexion refusée) dans un test Spring : WireMock sur un port fixe choisi au démarrage, `stop()` puis `start()` à la même adresse (exemple : `DeliveryNoteApiTest`). Le contexte Spring garde l'URL, un port dynamique changerait au redémarrage.
- Nouvelles tentatives Kafka en test : chaque tentative coûte ~0,5 s de plus que son backoff (relecture après le seek, `fetch.max.wait.ms`) ; les attentes d'un test doivent en tenir compte (exemple : `ScanEventListenerTest`, backoff raccourci par propriétés). Le topic `scan-events` des tests est créé automatiquement avec une seule partition.
- `ApplicationContextRunner` avec un `@Value Duration` (`"1s"`) : ajouter `ApplicationConversionService.getSharedInstance()` comme service de conversion (exemple : `KafkaConsumerConfigTest`).
- `DeadLetterApiTest` a son propre contexte, donc son propre DLT, et ses tests appellent « tout rejouer » : un test qui y envoie un message en DLT doit le rendre rejouable sans échec (scan d'expédition inconnue, puis création de l'expédition), sinon un second « tout rejouer » ne rend plus 0. « Tout rejouer » est asynchrone (202, puis `GET …/replay/{id}` jusqu'à `finished`, helper `replayAll()`), et un rejeu n'est publié que par l'outbox (tâche planifiée toutes les secondes dans chaque contexte de test) : attendre l'effet, jamais le supposer immédiat.
- Tester un format de journal (profil `gcp`, #64) : Spring Boot ne reconfigure pas la journalisation de la JVM qu'un contexte précédent a déjà configurée ; appeler `LoggingSystem.cleanUp()` avant et après le test (exemple : `GcpLoggingTest`, ai-service).
- Finales (implémenteur) : `./mvnw -B -ntp verify` dans les trois services (compilation, tests, règles ArchUnit). Pas de suite e2e.
- Suite complète (orchestrateur, au passage de main) : `verify` des trois services.

## Préparer un worktree
- Rien à copier : aucun secret n'est nécessaire aux tests (WireMock pour l'API Claude, Testcontainers pour PostgreSQL et Kafka).
- Docker doit tourner (OrbStack). Les tests ne fixent aucun port (Testcontainers choisit les siens) : deux implémenteurs en parallèle ne se gênent pas, mais chacun démarre ses conteneurs. Pas de `docker compose up` dans un worktree (ports 5432 et 9092 fixes, partagés).
- Le worktree isolé part de `main` : créer sa branche depuis la branche d'intégration (`git checkout -B <branche> <intégration>`) avant tout.
- Hook rtk : les commandes git locales (pas `git push`), `cd` et `./mvnw` sont autorisées par `~/.claude/settings.json` (#21). Dans un worktree isolé, `git` n'est pas réécrit en `rtk git` (enveloppe `~/.claude/hooks/rtk-worktree-git.sh`, #44) : `git`, `cd … && ./mvnw`, variables et heredocs passent (vérifié le 2026-11-06). Si une commande reste refusée : des appels simples, `/usr/bin/git`, et `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw …`.
- Lancer un service à la main (rare) : `PORT=<8090 + 10×slot + n>`, jamais 8081 ou 8082.

## Authentification (tests de shipment-service)
- Starters Spring Boot 4.1 : `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` et leurs `-test`.
- `TestcontainersConfiguration` importe `TestSecurityConfiguration` : clé RSA générée en mémoire (`jwt.private-key`) et ADMIN amorcé `test-admin` / `test-admin-password`. `TestAccounts` crée les comptes de test (`ensure`) et obtient un jeton par `POST /api/auth/token` (`adminBearer`, `operatorBearer`, `scannerBearer`).
- `POST /api/auth/token` : corps `{"username","password"}`, réponse `{"accessToken","tokenType":"Bearer","expiresAt"}`. `sub` = identifiant du compte.
- `POST /api/users` (ADMIN) : corps `{"username","password","role","site"}`, 201 + `Location`, réponse `{"id","username","role","site","active","createdAt"}`. `PATCH /api/users/{id}` : corps `{"active":false}` (désactive) ou `{"active":true}` (réactive, #25), idempotent, 200 + le compte ; `active` absent ou null : 400.
- Blocage après échecs (`LoginThrottle`, `auth.login.*`) : en mémoire, donc partagé par toutes les classes de test qui partagent le contexte Spring. Échouer la connexion avec un identifiant propre au test, jamais 5 fois avec `test-admin` ; une connexion réussie remet le compteur à zéro.

## Anomalies (shipment-service, #39)
- Port `AnomalyRepository` : `openIfAbsent` (INSERT … ON CONFLICT sur l'index partiel de V4, false si déjà ouverte), `resolveOpen`, `findOpen`. Cas d'usage `DetectAnomaliesService.detect()`, bean créé par `infrastructure/scheduling/AnomalyDetectionConfig` (`shipment.anomaly.stalled-after`).
- Tâche planifiée : première passe un intervalle (15 min) après le démarrage, donc jamais pendant les tests ; les tests appellent `detect()` directement. La base est partagée entre classes de test : `detect()` ouvre aussi des anomalies sur les expéditions des autres classes, ne vérifier que les siennes.
- Un scan (`RecordScanService`) et la détection verrouillent l'expédition (`lockByReference`, `lockById`, SELECT … FOR UPDATE) : sans ce verrou, une STALLED pouvait s'ouvrir juste après la clôture par un scan concurrent.

## Projet
- Branche de base des specs : `main`. Ne rien pousser sans Michael.
- Journal des décisions : `.claude/rules/architecture.md` > Décisions (dépendances Maven : à faire valider par Michael avant de les ajouter).
- Notes des agents : `docs/notes/` (index : `docs/notes/README.md`), à créer avec la première note.
- Modèle de domaine : aucun glossaire ; modèle métier dans `architecture.md` (Site, Expédition, ÉvénementDeScan, Anomalie).
- Points ouverts : label `point-ouvert`, plus `action Michael` ou `à trancher`.
- Fichier de durées : aucun.

## Tickets
- `opus / high` pour : émission des jetons, comptes et mots de passe ; la validation de jetons dans un service consommateur reste en `sonnet / medium` si la spec fixe les cas négatifs à tester (décidé le 2026-11-03) ; consommateur Kafka (idempotence, DLT, ordre), modèle du domaine et transitions de statut.
- Seuls (jamais en parallèle d'un autre ticket) : changement d'un contrat de `contracts/` (producteur et consommateur à changer ensemble), migration Flyway.
- Fichiers partagés (arête de blocage) : `contracts/*.json`, `docker-compose.yml`, `.github/workflows/ci.yml`, les `pom.xml`, `db/migration/` de `shipment-service` (réserver les numéros `V<n>__` d'avance).
- Interdits propres au projet : jamais d'appel à la vraie API Claude (chaque appel coûte) ; `domain/` n'importe ni Spring, ni JPA, ni Kafka ; aucune bibliothèque partagée entre services.

## Import CSV (ingestion-service, #78)
- `ScanCsvImporter.importCsv(byte[], site)` (infrastructure/batch) rend un `ImportReport` ou lève `CsvStructureException` (avant toute publication), `ImportBusyException(retryAfter)` (429, attente `ingestion.import.wait`), `ImportInterruptedException` (`published()`, 503). Le contrôleur passe par `ImportScansService` (application) et le port `ScanImport`, que `ScanCsvImporter` implémente ; les lignes sont validées par Bean Validation (`ScanRowDraft`, à garder alignée sur `ScanRequest`). Route et erreurs (#79) : `ScanImportController`, `ApiExceptionHandler` ; tests `ScanImportApiTest` (MockMvc, `upload(csv, token)` réutilisable), `ScanImportErrorsTest` (`@MockitoBean ScanCsvImporter`), `ScanImportLimitTest` (413, vrai serveur), `ScanImportGcpLoggingTest`. Test : un `ScanPublisher` de test qui échoue au n-ième lot, `new ScanCsvImporter(jobRepository, jobOperator, publisher)` (constructeur du paquet). Le `DelimitedLineTokenizer` de Spring Batch 6 complète les cellules manquantes quand ses noms sont posés : ne pas les poser pour compter les colonnes.
