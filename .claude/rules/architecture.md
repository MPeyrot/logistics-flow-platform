# Architecture — plateforme de suivi de flux logistiques

## Décisions
- **Microservices** Spring Boot (Java 21, Maven) dans un **monorepo**.
- **Hexagonale** dans chaque service : `domain/` (aucune dépendance Spring), `application/` (cas d'usage, ports), `infrastructure/` (REST, JPA/Hibernate, Kafka, client Claude).
- **Front** : Vue 3 + TypeScript + Pinia + Vite.
- **Déploiement** : Docker Compose en local, GCP Cloud Run ensuite. CI : GitHub Actions.
- Décidé par Michael le 2026-10-31 :
  - **Authentification** : `shipment-service` émet les JWT, les autres services les valident.
  - Le **front** appelle seulement `shipment-service`, qui appelle `ai-service`.
  - **Messages Kafka** : JSON, schémas versionnés dans `contracts/`.
  - **Kafka** en local (Docker Compose) et en ligne : Kafka managé auquel les services Cloud Run se connectent (offre à choisir, issue #2).
  - **Modèle métier** : Site, Expédition, ÉvénementDeScan, Anomalie ; Compte (2026-11-03, rôles ADMIN, OPERATOR, SCANNER ; un SCANNER est rattaché à un site).
  - **`JAVA_HOME`** propre au projet : `source scripts/env.sh` avant `./mvnw`, sans modifier `~/.zshrc`.
- Décidé par Michael le 2026-11-05 (à implémenter, issues ouvertes) :
  - **Statuts d'expédition** (#1) : CREATED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED. Transitions : CREATED → IN_TRANSIT (PICKED_UP), IN_TRANSIT → IN_TRANSIT (ARRIVED_AT_SITE), IN_TRANSIT → OUT_FOR_DELIVERY (OUT_FOR_DELIVERY), OUT_FOR_DELIVERY → DELIVERED (DELIVERED) ou DELIVERY_FAILED (DELIVERY_FAILED), DELIVERY_FAILED → IN_TRANSIT (ARRIVED_AT_SITE). Nouveaux types de scan dans un contrat `scan-event.v2` (changement cassant, producteur et consommateur ensemble).
  - **Retard** (#1) : deux types d'Anomalie. LATE : non livrée après `expectedDeliveryAt` (nouveau champ saisi à la création). STALLED : aucun scan depuis 48 h (configurable) sur une expédition non livrée.
  - **Kafka en ligne** (#2) : Confluent Cloud (essai 30 jours), avec un profil de repli sans Kafka (REST direct derrière `ScanEventPublisher`) prêt pour la fin de l'essai.
  - **Dépôt** (#3) : public, après un leakproof propre et la fusion de la spec en cours ; README marqué « projet personnel ».
  - **Scan avant l'expédition** (#4) : « expédition inconnue » retentée avec un backoff exponentiel jusqu'à ~5 min, puis DLT. Endpoint ADMIN pour lister `scan-events.DLT` et rejouer un message ou tous vers `scan-events`.
- Décidé par Michael le 2026-11-05, pour la spec « cycle de vie des expéditions » (#1, #4) :
  - **Nouvelles tentatives** « expédition inconnue » : bloquantes (backoff exponentiel dans le `DefaultErrorHandler`), pour garder l'ordre par expédition ; la partition attend jusqu'à ~5 min. Pas de topics de retentative.
  - **Bascule `scan-event.v2`** : coupure nette, sans champ de version ; les types v1 sont un sous-ensemble de v2. Motif facultatif `reason` (≤ 200 caractères), accepté seulement avec DELIVERY_FAILED (400 sinon).
  - **`expectedDeliveryAt`** obligatoire à la création ; NULL pour les expéditions déjà en base (jamais LATE).
  - **Anomalies** : tâche planifiée dans `shipment-service`, enregistrées en table (une seule ouverte par expédition et type, même avec plusieurs instances). Clôture automatique : STALLED au scan suivant, LATE à la livraison. STALLED compte depuis la réception du dernier scan (revu le 2026-11-06, voir plus bas), ou depuis `createdAt` sans scan. Lecture : `GET /api/anomalies` et anomalies ouvertes dans la réponse d'une expédition.
  - **Rejeu du DLT** : rejeux enregistrés en table, « tout rejouer » saute les messages déjà rejoués.
  - Une seule migration `V4__` pour toute la spec ; spec/13 fusionnée dans `main` avant le lancement.
- Validé par Michael le 2026-11-03 (#14, #15, #16) : dans les trois services, `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` et leurs starters de test ; `wiremock-standalone` 3.13.2 en test dans `ingestion-service`.
- Décidé par Michael le 2026-11-06 (#43, #46, #48) : `spring.kafka.listener.concurrency=3` gardé dans `shipment-service` (seule la partition de l'expédition attendue gèle) ; sur Cloud Run, `shipment-service` tourne avec `min-instances` ≥ 1 (détection des anomalies et consommateur Kafka), le délai initial de 15 min reste ; liste du DLT : plus récents d'abord.
- Décidé par Michael le 2026-11-06 (revue finale de #33) : **STALLED compte depuis l'heure de réception** du dernier scan par `shipment-service` (`scan_event.received_at`, horloge du service, migration `V5__`), sinon depuis `createdAt`. `occurredAt` reste la date métier, fournie par le client : un scan en retard (scanner hors ligne, rejeu du DLT) ne rouvre plus STALLED aussitôt, un scan daté dans le futur ne l'empêche plus. Scans antérieurs à `V5__` : `received_at` = `occurred_at`.
- Décidé par Michael le 2026-11-06 (revue finale de #33, #50, #51) : `ScanEventMessage` (shipment-service) est contrôlé par Bean Validation avant d'entrer dans `application` ; un échec = message illisible, DLT immédiat. `ingestion-service` répond en 400 `problem+json` (champ nommé, sans le message de Jackson) à un JSON illisible ou une valeur inconvertible, comme `shipment-service` (#38). `occurredAt` n'accepte qu'un texte ISO-8601 (comme `expectedDeliveryAt`). `reason` : 200 points de code (pas des unités UTF-16) et blancs du `\S` ECMAScript du contrat (espace insécable compris), dans les deux services.
- Décidé par Michael le 2026-11-06 (revue finale de #33) : **rejeu du DLT par outbox** (le rejeu écrit `dlt_replay` et le message à publier dans `dlt_replay_outbox`, même transaction ; un publieur publie puis marque après l'accusé, migration `V6__`) et **« tout rejouer » asynchrone** (202 et un identifiant, tâche de fond, avancement par une route GET).
- Décidé par Michael le 2026-11-06 (#53, #54) : un « tout rejouer » interrompu par l'arrêt de l'instance reste « non terminé » ; l'ADMIN le relance (les messages déjà rejoués sont sautés), pas de détection d'abandon. Pas de purge des lignes publiées de `dlt_replay_outbox` (volume faible).

## Structure
```
javaProject/
  services/
    shipment-service/    API expéditions, PostgreSQL, Hibernate, Flyway ; consomme les scans Kafka
    ingestion-service/   point d'entrée des scans (HTTP, puis import Spring Batch) : valide et publie sur Kafka
    ai-service/          appel API Claude (extraction bon de livraison, explication d'anomalies) ; sans base de données
  contracts/             schémas JSON des messages Kafka (versionnés)
  frontend/              Vue 3 + TS
  docker-compose.yml
  .github/workflows/
```
Chaque service : `src/main/java/<package>/{domain,application,infrastructure}`.

## Règles
- `domain` n'importe ni Spring, ni JPA, ni Kafka. Les entités JPA vivent dans `infrastructure` et sont converties vers le domaine.
- Les services ne partagent pas de base de données. Ils échangent par REST ou Kafka.
- Toute donnée externe (requête HTTP, réponse du modèle, message Kafka) est validée (Bean Validation) avant d'entrer dans `application`.
- Tests : JUnit 5 ; Testcontainers (PostgreSQL, Kafka) ; WireMock pour l'API Claude. Aucun test n'appelle la vraie API.
- Migrations SQL uniquement via Flyway.
- Clés d'API et secrets en variables d'environnement, jamais dans le dépôt (vérifié avec leakproof avant publication).
- Ce projet est personnel : il ne s'écrit jamais sur un CV comme de l'expérience en production.

## Authentification (spec #13, décidé le 2026-11-03)
- JWT RS256 émis par `shipment-service` (`POST /api/auth/token`), clé publique sur `/.well-known/jwks.json` ; `ingestion-service` et `ai-service` valident avec le resource server Spring Security (`SHIPMENT_SERVICE_URL`), sans secret.
- Claims : `iss=shipment-service`, `aud` = service visé, `roles`, `site` (SCANNER). Durées : 1 h (ADMIN, OPERATOR), 12 h (SCANNER), 5 min (jeton de service `shipment-service` → `ai-service`). Pas de refresh token ; révocation = désactivation du compte ; un jeton déjà émis reste valable jusqu'à son expiration (confirmé par Michael le 2026-11-03, #27).
- `ai-service` n'accepte que `sub=shipment-service`, `aud=ai-service`. `ingestion-service` : rôle SCANNER et site du scan = claim `site`.
- `shipment-service` valide ses propres jetons avec sa clé publique : `iss`, `exp`, et `aud` égal à `shipment-service` ou `ingestion-service` (un SCANNER reçoit ainsi 403, pas 401 ; le jeton de service `aud=ai-service` est refusé). Confirmé par Michael le 2026-11-03 (#22).
- Clé privée `JWT_PRIVATE_KEY` (PEM PKCS#8) obligatoire, générée en local par `scripts/generate-jwt-key.sh` dans `.secrets/` (ignoré par git), chargée par `scripts/env.sh`. Les tests génèrent leur clé.
- `domain` et `application` n'importent pas `org.springframework.security` (règle ArchUnit).
- Limites de débit en mémoire, par instance, 429 + `Retry-After` : 20 extractions/h par utilisateur (shipment ; rendue sur un 415 ou 429 d'ai-service, et sur ai-service injoignable — connexion refusée, décidé le 2026-11-04, #29 ; une expiration de lecture reste comptée), 100/h au total (ai ; rendu sur clé absente ou erreur de l'API Claude sans réponse du modèle), blocage de 15 min après 5 échecs de connexion.
- Connexion (décidé le 2026-11-03, #23, #24) : identifiant bloqué → 429 + `Retry-After`, aussi pour un identifiant inconnu ; une connexion réussie remet le compteur à zéro ; une tentative compte comme échec dès son début (annulée si une erreur inattendue l'interrompt, base indisponible par exemple) ; au-delà de 10 000 identifiants suivis, les moins récemment vus sont oubliés, les identifiants bloqués en dernier : un bloqué n'est oublié qu'à la fin de son blocage, sous un second plafond de taille (décidé le 2026-11-04, #28 ; 100 000 par défaut, `auth.login.max-blocked-usernames`). Un ADMIN peut réactiver un compte désactivé (`PATCH /api/users/{id}`, `active`).

## Matrice d'accès de shipment-service (spec #13, complétée par #33/#40)
Sans jeton : 401 ; jeton valide mais rôle non permis : 403. `GET`/`POST /api/shipments`, `POST /api/delivery-notes/extract` : ADMIN, OPERATOR. `GET /api/anomalies` (`open=true|false`, tri `detectedAt` décroissant, `open` invalide : 400) : ADMIN, OPERATOR. `POST /api/users`, `PATCH /api/users/{id}` : ADMIN. SCANNER : aucune route de shipment-service. `GET /api/shipments` porte `openAnomalies` (`type`, `detectedAt`) par expédition, lues en une requête (pas de N+1). Tout le reste : denyAll.

## Statuts d'expédition (spec #33, #35)
- `ShipmentStatus` : CREATED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED. `ScanType` (domaine) : PICKED_UP, ARRIVED_AT_SITE, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED.
- `Shipment.applyScan` permet six transitions : CREATED + PICKED_UP → IN_TRANSIT ; IN_TRANSIT + ARRIVED_AT_SITE → IN_TRANSIT ; IN_TRANSIT + OUT_FOR_DELIVERY → OUT_FOR_DELIVERY ; OUT_FOR_DELIVERY + DELIVERED → DELIVERED ; OUT_FOR_DELIVERY + DELIVERY_FAILED → DELIVERY_FAILED ; DELIVERY_FAILED + ARRIVED_AT_SITE → IN_TRANSIT. Toute autre combinaison lève `InvalidScanException` (DLT immédiat côté Kafka) : `IN_TRANSIT → DELIVERED` direct est refusé, DELIVERED est terminal.
- `ScanEvent` (domaine, dans les deux services) porte `reason` : facultatif, seulement avec DELIVERY_FAILED, non vide, ≤ 200 caractères (sinon `IllegalArgumentException`). Enregistré dans `scan_event.reason` (#37).

## Messagerie Kafka
- Topic `scan-events` (3 partitions), clé = référence d'expédition (ordre garanti par expédition), valeur = JSON `contracts/scan-event.v2.schema.json` (#37 ; v1 reste dans le dépôt, ses messages sont des messages v2 valides). Le producteur (`ingestion-service`) possède le topic.
- `reason` (v2) : omis du JSON quand il est absent (jamais `null`). Refusé par `POST /api/scans` (400 `ProblemDetail`, `detail` = champs fautifs) s'il accompagne un autre type que DELIVERY_FAILED, est vide ou blanc, ou dépasse 200 caractères ; côté consommateur, le même message est illisible (DLT immédiat).
- Aucune bibliothèque partagée entre services : chacun a sa propre classe de message, le contrat JSON fait foi. Pas d'en-têtes de type Java dans les messages.
- Consommateur (`shipment-service`) : idempotent (clé primaire `scan_event.event_id`), nouvelles tentatives pour les erreurs transitoires (expédition inconnue, base indisponible), envoi direct vers `scan-events.DLT` pour un message illisible ou une transition interdite.
- Expédition inconnue (#36) : backoff exponentiel bloquant (1 s, ×2, plafond 60 s, arrêt une fois 5 min d'attente cumulée, soit ~5 min ; `shipment.kafka.unknown-shipment.*`), puis DLT ; base indisponible : 1 s × 5. Gel accepté pour garder l'ordre par expédition : le consommateur dort sans `poll()`, ses partitions attendent (une par consommateur avec `spring.kafka.listener.concurrency=3`). Chaque attente, plus une marge pour traiter les messages du lot qui précèdent l'échec (`shipment.kafka.batch-processing-margin`, 60 s), reste sous `max.poll.interval.ms`, fixé à 5 min : pas de rééquilibrage ; démarrage refusé si `unknown-shipment.max-interval` ou `retry-interval-ms`, plus la marge, l'atteint. Chaque tentative ajoute ~0,5 s de relecture (`fetch.max.wait.ms`).
- DLT (#41) : `shipment-service` déclare `scan-events.DLT` (3 partitions). En-têtes `kafka_dlt-exception-*` = l'erreur de l'écouteur, désenveloppée de `ListenerExecutionFailedException`. Outil ADMIN `/api/admin/dlt/scan-events` : lecture par un consommateur sans groupe (`assign`, rien de commité), messages les plus récents d'abord ; rejeu = enregistré dans `dlt_replay` et mis dans `dlt_replay_outbox` (clé et valeur), dans la même transaction (`ON CONFLICT DO NOTHING` : un rejeu concurrent du même message donne 409). Publieur de l'outbox (`DeadLetterOutboxPublisher`, toutes les secondes) : republie vers `scan-events` sans en-têtes, dans l'ordre d'écriture, marque chaque ligne après l'accusé ; un échec de publication arrête la passe, la suivante repart de ce message ; un seul publieur à la fois toutes instances comprises (`pg_try_advisory_xact_lock`) ; au moins une fois (écouteur idempotent). « Tout rejouer » (`POST …/replay`) : 202, `Location` vers `GET …/replay/{id}` (`replayed` = mis dans l'outbox, `published`, `failed`, `finished`, `error`) ; tâche de fond sur `applicationTaskExecutor`, qui parcourt les messages présents à son début, partition par partition, dans l'ordre des offsets. Une instance arrêtée en cours de route laisse le rejeu non terminé ; le relancer reprend sans doublon.
- Tests : Testcontainers avec `apache/kafka:3.9.1`, la même version que `docker-compose.yml` (la 3.9.0 ne démarrait pas avec Testcontainers).

## ai-service
- `POST /api/delivery-notes/extract` (multipart `file` : PDF, PNG, JPEG, GIF ou WebP, reconnu par ses premiers octets). Réponse : `deliveryNote`, `usage` (jetons) et `costUsd`.
- SDK Java officiel `com.anthropic:anthropic-java`, utilisé uniquement dans `infrastructure/claude` (règle ArchUnit). Port `DeliveryNoteExtractor` côté `application`.
- Réponse du modèle = donnée externe : JSON lu dans `DeliveryNoteDraft`, contrôlé par Bean Validation, puis converti en `DeliveryNote` (domaine). JSON invalide, référence absente ou valeur hors bornes : 422.
- Pas de `tool_choice` forcé (refusé par claude-opus-5-5) ni de réflexion configurée : consignes de format dans le prompt système, contrôle après coup.
- Prompt système constant et marqué `cache_control` (cache de prompt). Il ne reste en cache que s'il dépasse le seuil minimal du modèle. Mesuré le 2026-11-05 (#8) : ~1 400 caractères, sous le minimum de 512 jetons d'Opus, Sonnet et Haiku 5.5, donc `cache_creation` et `cache_read` restent à 0 ; on ne le gonfle pas (gain au mieux ~10 % du coût d'une extraction).
- Coût par requête : `ModelPricing` (domaine) × `TokenUsage`, une table de tarifs par modèle dans `ai.claude.pricing.<modèle>.*` (dollars par million de jetons ; claude-haiku-5-5, claude-sonnet-5-5, claude-opus-5-5), journalisé et renvoyé dans la réponse. Le service refuse de démarrer si `AI_CLAUDE_MODEL` n'a pas de tarif complet (#10).
- Clé : `ANTHROPIC_API_KEY` (absente : 503 sur l'extraction, le service démarre quand même). Modèle : `AI_CLAUDE_MODEL`, défaut `claude-haiku-5-5` (décidé par Michael le 2026-11-05, #9 : 33 champs sur 33 justes sur 5 bons fictifs, ~0,0004 $ l'extraction contre ~0,015 $ avec Opus 5.5) ; le tarif suit le modèle.
- Tests : WireMock simule l'API Claude (`ANTHROPIC_BASE_URL` / `ai.claude.base-url`).
- `shipment-service` appelle `ai-service` (port `DeliveryNoteReader`, adaptateur `infrastructure/ai` en `RestClient`, HTTP/1.1, lecture jusqu'à 120 s, URL dans `AI_SERVICE_URL`). `POST /api/delivery-notes/extract` rend le bon lu, son coût et l'expédition de même référence (`shipment` null si inconnue). Erreurs relayées : 415, 422 et 429 tels quels, tout le reste 502 (401 et 403 compris : erreur de configuration de notre côté). La réponse de l'ai-service est validée avant d'entrer dans `application`. Aucune expédition n'est créée ni modifiée.
