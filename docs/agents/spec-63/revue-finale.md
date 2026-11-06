# Revue finale — spec #63

Revue `code-review` medium depuis `ca50ff3` (opus, 2026-11-06). Elle n'a relevé que des défauts du code antérieur à la spec, dans des fichiers que la spec touche ou appelle (ai-service, tests de shipment-service). Les fichiers de déploiement (#64 à #67) font l'objet d'une seconde revue ciblée (points 11 et suivants, ajoutés à son retour).

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

## Hors lots (issues)

- #71 (point 1 de la revue) : un appel en `ExtractionFailedException` (expiration de lecture après 60 s × 3 essais) n'est pas imputé au budget quotidien. Contredit une décision existante (« rendu sur … erreur de l'API Claude sans réponse du modèle ») : à trancher par Michael.
- #72 (point 7) : `AI_DAILY_BUDGET_USD=0` fait échouer le démarrage au lieu de tout bloquer ; valeur absente ou non numérique : erreur peu claire. À trancher.
- #73 (point 10) : `ClaudeProperties.Pricing` / `LongContext` répètent les contrôles de `RateCard` (domaine). Nettoyage, point ouvert.
