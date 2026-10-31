# Architecture — plateforme de suivi de flux logistiques

## Décisions
- **Microservices** Spring Boot (Java 21, Maven) dans un **monorepo**.
- **Hexagonale** dans chaque service : `domain/` (aucune dépendance Spring), `application/` (cas d'usage, ports), `infrastructure/` (REST, JPA/Hibernate, Kafka, client Claude).
- **Front** : Vue 3 + TypeScript + Pinia + Vite.
- **Déploiement** : Docker Compose en local, GCP Cloud Run ensuite. CI : GitHub Actions.

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

## Messagerie Kafka
- Topic `scan-events` (3 partitions), clé = référence d'expédition (ordre garanti par expédition), valeur = JSON `contracts/scan-event.v1.schema.json`. Le producteur (`ingestion-service`) possède le topic.
- Aucune bibliothèque partagée entre services : chacun a sa propre classe de message, le contrat JSON fait foi. Pas d'en-têtes de type Java dans les messages.
- Consommateur (`shipment-service`) : idempotent (clé primaire `scan_event.event_id`), nouvelles tentatives pour les erreurs transitoires (expédition inconnue, base indisponible), envoi direct vers `scan-events.DLT` pour un message illisible ou une transition interdite.
- Tests : Testcontainers avec `apache/kafka:3.8.0` (la 3.9.0 ne démarre pas avec Testcontainers).

## ai-service
- `POST /api/delivery-notes/extract` (multipart `file` : PDF, PNG, JPEG, GIF ou WebP, reconnu par ses premiers octets). Réponse : `deliveryNote`, `usage` (jetons) et `costUsd`.
- SDK Java officiel `com.anthropic:anthropic-java`, utilisé uniquement dans `infrastructure/claude` (règle ArchUnit). Port `DeliveryNoteExtractor` côté `application`.
- Réponse du modèle = donnée externe : JSON lu dans `DeliveryNoteDraft`, contrôlé par Bean Validation, puis converti en `DeliveryNote` (domaine). JSON invalide, référence absente ou valeur hors bornes : 422.
- Pas de `tool_choice` forcé (refusé par claude-opus-5-5) ni de réflexion configurée : consignes de format dans le prompt système, contrôle après coup.
- Prompt système constant et marqué `cache_control` (cache de prompt). Il ne reste en cache que s'il dépasse le seuil minimal du modèle.
- Coût par requête : `ModelPricing` (domaine) × `TokenUsage`, tarifs dans `ai.claude.pricing.*` (dollars par million de jetons), journalisé et renvoyé dans la réponse.
- Clé : `ANTHROPIC_API_KEY` (absente : 503 sur l'extraction, le service démarre quand même). Modèle : `AI_CLAUDE_MODEL`, défaut `claude-opus-5-5`.
- Tests : WireMock simule l'API Claude (`ANTHROPIC_BASE_URL` / `ai.claude.base-url`).
