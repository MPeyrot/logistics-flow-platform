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
    ai-service/          appel API Claude (extraction bon de livraison, explication d'anomalies)
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
