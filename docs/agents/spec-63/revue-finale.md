# Revue finale — spec #63

Revue `code-review` medium depuis `ca50ff3` (opus, 2026-11-06). Elle n'a relevé que des défauts du code antérieur à la spec, dans des fichiers que la spec touche ou appelle (ai-service, tests de shipment-service). Les fichiers de déploiement (#64 à #67) font l'objet d'une seconde revue ciblée (lots C et E).

## Lot A — ai-service, facturation et journal (#69)

- **A1 (#69, décidé par Michael)** `services/ai-service/src/main/java/fr/michael/logistics/ai/application/ExtractDeliveryNoteService.java:25` : la ligne de journal `costUsd` / `model` / `outcome` passe derrière le port `ExtractionMetrics` (adaptateur `infrastructure/metrics`). Le service ne prend plus le modèle ni `@Value`. `GcpLoggingTest` reste vert. Noter la décision dans `.claude/rules/architecture.md` (section ai-service).
- **A2** même fichier, `:41` et `:57` : `budget.record` + `metrics.recordBilledCall` + journal copiés dans deux chemins. Un seul helper privé (`billed(usage, outcome)`).
- **A3** même fichier, `:38` : une `RuntimeException` imprévue après la réponse du modèle (conversion, validation, lecture de l'usage dans l'adaptateur) sort sans `budget.record` ni métrique, alors que l'appel est facturé. Correction attendue : dans l'adaptateur `infrastructure/claude`, toute erreur survenue après réception d'une réponse (usage connu) devient `UnreadableDocumentException` avec l'usage ; un test le prouve (WireMock, réponse qui fait échouer la conversion).
- **A4** `services/ai-service/src/main/java/fr/michael/logistics/ai/application/DailyBudget.java:41` : `record()` impute le jour UTC de fin d'appel, `checkAvailable()` a vérifié celui du début. Imputer le jour du contrôle (le retenir au `checkAvailable`, ou le passer à `record`). Test avec une horloge fixe qui passe minuit.
- **A5** `services/ai-service/src/main/java/fr/michael/logistics/ai/infrastructure/metrics/MicrometerExtractionMetrics.java:31` : modèle relu par `@Value("${ai.claude.model}")` au lieu de `ClaudeProperties.model()`. Injecter `ClaudeProperties` (même source que le tarif).

## Lot B — isolation des tests de shipment-service (#61)

- **B1** `services/shipment-service/src/test/java/fr/michael/logistics/shipment/infrastructure/persistence/DeadLetterOutboxLockTest.java:17` : réactive le publieur de l'outbox à 1 s dans un contexte mis en cache, sans `@DirtiesContext` : il publie pendant les autres classes. Retirer la surcharge (le test n'appelle que `lockPublisher()`) ou ajouter `@DirtiesContext`.
- **B2** `services/shipment-service/src/test/resources/config/application.properties:6` : `shipment.kafka.dlt.outbox.interval=1h` n'empêche pas la passe au démarrage de chaque contexte (`@Scheduled(fixedDelayString)` sans `initialDelay`). Couper la tâche par une propriété (`@ConditionalOnProperty` ou `initialDelay` configurable) ; `ScanEventListenerTest` et `DeadLetterApiTest` la réactivent.
- **B3** `services/shipment-service/src/test/java/fr/michael/logistics/shipment/infrastructure/web/DeadLetterApiTest.java:128` : Kafka partagé, « tout rejouer » rejoue aussi les messages de DLT laissés par `ScanEventListenerTest`, qui y retournent : le résultat dépend de l'ordre des classes. Ne vérifier que les messages produits par le test (références propres).

## Lot C — infrastructure GCP (revue ciblée du déploiement)

- **C1 (haute)** `scripts/deploy/infra.sh:147` (liaison l.159) : la condition WIF ne vérifie que `assertion.repository` ; `attribute.ref` est déclaré mais inutilisé. Un workflow de n'importe quelle branche peut agir comme `github-deployer` (`run.admin`, `actAs` sur les comptes qui lisent `JWT_PRIVATE_KEY` et la base). Ajouter `&& assertion.ref=='refs/heads/main'` à la condition, et mettre à jour `architecture.md` (Ressources GCP).
- **C2** `scripts/deploy/setup-wizard.sh:274` : URL JDBC Neon en `sslmode=require` (chiffré sans vérifier le certificat). Passer en `sslmode=verify-full` avec le magasin de certificats de la JVM (`sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`), à confirmer dans la doc de pgjdbc ; non testable sans Neon : le noter dans #68.
- **C3** `scripts/deploy/infra.sh:79` : après `gcloud services enable`, les créations (Artifact Registry, secrets, comptes de service, métrique) n'ont pas de `retry` (seules les liaisons IAM en ont) : `SERVICE_DISABLED` sur un projet neuf interrompt le premier passage. Les passer par `retry` ou attendre l'activation des API.
- **C4** `scripts/deploy/infra.sh:194` : la métrique `ai-claude-cost` est créée si absente mais jamais mise à jour. Si elle existe : `gcloud logging metrics update --config-from-file`.
- **C5** `scripts/deploy/setup-wizard.sh:159` et `:173` : `gh variable set` / `gh secret set` sans `--repo "$GITHUB_REPO"` (le dépôt visé dépend du remote `origin`). Ajouter `--repo`.

## Lot D — ai-service et profil gcp (#71, #72)

- **D1 (#71, décidé par Michael)** `services/ai-service/src/main/resources/application.properties:13` : `ai.claude.max-retries=2` passe à 1. La règle reste : rien d'imputé sans réponse du modèle. Mettre à jour `architecture.md` (section ai-service).
- **D2 (#72, décidé par Michael)** `services/ai-service/src/main/java/fr/michael/logistics/ai/infrastructure/web/QuotaConfig.java:20` (et `DailyBudget`) : `AI_DAILY_BUDGET_USD=0` accepté, toute extraction rend 429 sans appel au modèle ; négatif, absent ou non numérique : refus au démarrage avec un message clair. Tests. `architecture.md` (section ai-service).
- **D3** `application-gcp.properties:4` (les 3 services) : `level` renommé `severity` garde les valeurs de Logback (`WARN`, `TRACE`) ; Cloud Logging attend `WARNING`, `DEBUG`… Faire correspondre les valeurs (customizer de journal structuré Spring Boot, par exemple `StructuredLoggingJsonMembersCustomizer`, dans chaque service) ; test qui vérifie `WARNING` sur une ligne `WARN`.

## Lot E — workflows et scripts (#70)

- **E1 (#70)** `scripts/env.sh:1`, `:3`, `:9` : shellcheck (que la CI lance sur tous les `scripts/*.sh`) relève SC2148 (pas de shebang : ajouter `# shellcheck shell=bash`, le fichier est sourcé) et SC2155 ×2 (déclarer puis assigner). `shellcheck scripts/*.sh scripts/deploy/*.sh` doit être vide (installé en local).
- **E2** `scripts/deploy/smoke.sh:53` : mots de passe passés en argument (`jq --arg`, `curl --data "$json"`), visibles dans `ps`. Corps par `curl --data @-`, valeurs lues par jq depuis l'environnement ou stdin.
- **E3** `.github/workflows/deploy-up.yml:113` : séparateur `^@^` de `--set-env-vars`, alors que `setup-wizard.sh:448` accepte un identifiant avec `@`. Choisir un séparateur impossible dans un identifiant, et valider l'identifiant dans l'assistant.
- **E4** `.github/workflows/deploy-up.yml:59` : le garde « CI verte » ne regarde que les jobs `Tests (…` et ignore le job ShellCheck. Exiger tous les jobs de `ci.yml`.
- **E5** `.github/workflows/ci.yml:12` : `cancel-in-progress: true` s'applique aussi à `main` ; une CI annulée bloque `up` sur ce commit. `cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}`.

## Hors lots (issues)

- #71 (point 1 de la revue, décidé : lot D1) : un appel en `ExtractionFailedException` (expiration de lecture après 60 s × 3 essais) n'est pas imputé au budget quotidien. Contredit une décision existante (« rendu sur … erreur de l'API Claude sans réponse du modèle ») : à trancher par Michael.
- #72 (point 7, décidé : lot D2) : `AI_DAILY_BUDGET_USD=0` fait échouer le démarrage au lieu de tout bloquer ; valeur absente ou non numérique : erreur peu claire. À trancher.
- #73 (point 10) : `ClaudeProperties.Pricing` / `LongContext` répètent les contrôles de `RateCard` (domaine). Nettoyage, point ouvert.
