# Revue finale — spec #33

`code-review` en medium depuis 9d4f959 (merge-base avec `main`), le 2026-11-06. Chemins abrégés : `shipment/…` = `services/shipment-service/src/main/java/fr/michael/logistics/shipment/…`, `ingestion/…` = `services/ingestion-service/src/main/java/fr/michael/logistics/ingestion/…`.

## Points

1. **`shipment/domain/AnomalyRules.java:29`** (haute, à trancher par Michael). STALLED compte depuis `occurredAt`, fourni par le client. Un scan en retard (scanner hors ligne, rejeu du DLT) clôt STALLED, et la passe suivante la rouvre aussitôt. Un `occurredAt` dans le futur empêche STALLED pour toujours. Le code suit la lettre de #33 (« depuis le dernier scan »). **Décidé par Michael le 2026-11-06 : heure de réception.** STALLED compte depuis l'heure où shipment-service a reçu le dernier scan (nouvelle colonne, migration `V5__` réservée), sinon depuis `createdAt` ; `occurredAt` reste la date métier. Les scans déjà en base : heure de réception = `occurredAt` (ou au choix documenté de l'implémenteur). Mettre à jour la règle STALLED dans `architecture.md` (Décisions et section Anomalies si elle existe).
2. **`shipment/infrastructure/web/ShipmentResponse.java:23`** (moyenne). `from(Shipment)` renvoie toujours `openAnomalies` vide. `DeliveryNoteMatchResponse` l'utilise, donc `POST /api/delivery-notes/extract` dit « aucune anomalie » pour une expédition qui en a (story 10 de #33). Correction : charger les anomalies ouvertes de l'expédition trouvée (une requête).
3. **`shipment/application/DetectAnomaliesService.java:55`** (moyenne). Une exception sur une seule expédition (verrou, deadlock, coupure de base) sort de la boucle et arrête toute la passe. Correction : attraper et journaliser l'erreur par expédition, puis continuer.
4. **`shipment/application/DetectAnomaliesService.java:51`** (moyenne, performance). Chaque passe charge en mémoire toutes les anomalies ouvertes et toutes les expéditions non livrées, puis filtre en Java. Correction : une requête SQL qui ne rend que les candidates (non livrées ; échéance dépassée, ou dernier scan, sinon `createdAt`, au-delà du seuil ; sans anomalie ouverte de ce type).
5. **`shipment/application/DeadLetterService.java:57`** (moyenne). `replayAll` rejoue en série dans une seule requête HTTP : une transaction puis une attente d'accusé du broker (jusqu'à 10 s) par message. Avec des milliers de messages, la requête dépasse le délai de Cloud Run et l'ADMIN n'a aucun compte. **Décidé par Michael le 2026-11-06 (avec 6) : rejeu asynchrone.** « Tout rejouer » rend 202 et un identifiant de rejeu ; une tâche de fond publie ; l'ADMIN suit l'avancement (compte rejoué, en échec, terminé) par une route GET.
6. **`shipment/application/DeadLetterService.java:76`** (moyenne). La publication n'est pas transactionnelle : si le broker accuse réception et que le commit en base échoue, le message est republié sans ligne dans `dlt_replay`, puis republié une seconde fois par « tout rejouer ». **Décidé par Michael le 2026-11-06 : outbox.** Le rejeu écrit `dlt_replay` et le message à publier dans une table outbox dans la même transaction ; un publieur lit l'outbox et marque chaque ligne publiée après l'accusé. Migration `V6__` réservée.
7. **`shipment/infrastructure/messaging/KafkaConsumerConfig.java:44`** (basse). Le contrôle au démarrage compare seulement `unknown-shipment.max-interval` à `max.poll.interval.ms`. Il oublie le temps passé sur les messages précédents du lot (`max.poll.records`=500) et ne vérifie pas `retry-interval-ms`. Correction : exiger une marge (ou réduire `max.poll.records`) et vérifier aussi `retry-interval-ms`.
8. **`ingestion/domain/ScanEvent.java:29`** et `ScanEvent` de shipment-service (basse). La validation de `reason` ne suit pas `contracts/scan-event.v2` : longueur comptée en unités UTF-16 au lieu de points de code (le `maxLength` du schéma et `VARCHAR(200)` comptent les points de code), et l'espace insécable n'est pas traité comme un blanc alors que le motif du schéma le fait. Correction : compter les points de code et aligner la définition du blanc sur le schéma, dans les deux services.
9. **`ingestion/infrastructure/web/ScanRequest.java:18`** (basse). `occurredAt` accepte encore un nombre (epoch), contrairement à `expectedDeliveryAt` depuis #45. `@Size(min = 1)` sur `reason` fait doublon avec le `@Pattern` (deux messages d'erreur). Correction : ISO-8601 seulement, comme #45, et retirer le `@Size(min = 1)`.

Hors revue, décidés par Michael le 2026-11-06 (commentaires des issues) :

10. **#50** : handler dédié `HttpMessageNotReadableException` dans ingestion-service, même format que l'`ApiExceptionHandler` de shipment-service (#38). Fermer #50.
11. **#51** : Bean Validation sur `ScanEventMessage` (shipment-service), contrôlée avant d'entrer dans `application` ; un échec = message illisible, DLT immédiat. Ajouter la décision au journal d'`architecture.md`. Fermer #51.

## Lots

Dans cet ordre, un lot à la fois (D et B ont une migration) :

- **Lot D** (shipment-service, STALLED à l'heure de réception, `V5__`) : 1. opus / high.
- **Lot A** (shipment-service, anomalies ; après D, la requête du point 4 suit la nouvelle règle) : 2, 3, 4. sonnet / medium.
- **Lot C** (contrat scan-event, ingestion-service et lecture Kafka de shipment-service) : 8, 9, 10, 11. sonnet / medium.
- **Lot B** (shipment-service, DLT et Kafka, `V6__`) : 5, 6, 7. opus / high.
