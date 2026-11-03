# Logistics Flow Platform

[![CI](https://github.com/MPeyrot/logistics-flow-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/MPeyrot/logistics-flow-platform/actions/workflows/ci.yml)

Suivi de flux logistiques : des scanners envoient des événements (colis pris en charge, arrivé sur un site, livré), la plateforme met à jour l'état des expéditions de façon asynchrone.

Projet personnel pour s'exercer à une stack Java d'entreprise : Spring Boot, Kafka, PostgreSQL, architecture hexagonale, tests d'intégration avec de vrais conteneurs.

## Architecture

```mermaid
flowchart LR
    scanner([Scanner / client HTTP]) -->|POST /api/scans| ingestion[ingestion-service :8081]
    ingestion -->|scan-events| kafka[(Kafka)]
    kafka --> shipment[shipment-service :8080]
    kafka -.->|erreur| dlt[(scan-events.DLT)]
    shipment --> db[(PostgreSQL)]
    user([Utilisateur]) -->|GET/POST /api/shipments| shipment
    user -->|POST /api/delivery-notes/extract| shipment
    shipment -->|bon de livraison| ai[ai-service :8082]
    ai --> claude([API Claude])
```

| Dossier | Rôle |
| --- | --- |
| `services/ingestion-service` | Reçoit les scans en HTTP, les valide, les publie sur Kafka (accusé du broker attendu avant le `202`). |
| `services/shipment-service` | API des expéditions. Consomme les scans, applique les transitions de statut, garde l'historique. Relaie les bons de livraison à `ai-service` et retrouve l'expédition correspondante. |
| `services/ai-service` | Extrait les données d'un bon de livraison (PDF ou image) avec l'API Claude : JSON validé, jetons et coût de chaque requête. |
| `contracts/` | Schéma JSON versionné des messages Kafka. Pas de bibliothèque partagée entre services. |

Chaque service suit l'architecture hexagonale (`domain` / `application` / `infrastructure`), vérifiée par des tests ArchUnit. Le détail des choix est dans [`.claude/rules/architecture.md`](.claude/rules/architecture.md).

Points de fiabilité côté consommateur : traitement idempotent (clé primaire sur l'identifiant d'événement), nouvelles tentatives sur les erreurs transitoires, topic d'erreurs `scan-events.DLT` pour les messages illisibles ou les transitions interdites.

## Stack

Java 21, Spring Boot 4.1, Spring Data JPA / Hibernate, Flyway, PostgreSQL 17, Apache Kafka, Maven, JUnit 5, Testcontainers, ArchUnit, Docker Compose, GitHub Actions.

## Lancer en local

Prérequis : JDK 21, Docker.

```sh
scripts/generate-jwt-key.sh    # une fois : clé de signature des JWT dans .secrets/ (ignoré par git)
source scripts/env.sh          # JAVA_HOME sur openjdk@21 (macOS/Homebrew ; adapter ailleurs) et JWT_PRIVATE_KEY
docker compose up -d --wait    # PostgreSQL et Kafka

# premier ADMIN, créé au démarrage s'il n'en existe aucun (mot de passe : 12 caractères au moins)
(cd services/shipment-service && BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD=... ./mvnw spring-boot:run) &
(cd services/ingestion-service && ./mvnw spring-boot:run) &
(cd services/ai-service && ANTHROPIC_API_KEY=... ./mvnw spring-boot:run) &   # clé d'API Claude, jamais dans le dépôt
```

Exemple :

```sh
# 0. obtenir un jeton (valable 1 h) : les routes de shipment-service l'exigent
TOKEN=$(curl -s -X POST localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"..."}' | jq -r .accessToken)

# 1. créer une expédition
curl -X POST localhost:8080/api/shipments -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reference":"REF-1001","origin":"Nantes","destination":"Lyon"}'

# 2. envoyer un scan (traité en asynchrone via Kafka)
curl -X POST localhost:8081/api/scans -H 'Content-Type: application/json' \
  -d '{"shipmentReference":"REF-1001","type":"PICKED_UP","site":"Nantes"}'

# 3. le statut est passé de CREATED à IN_TRANSIT
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/shipments

# 4. lire un bon de livraison (PDF ou image) : données extraites, coût, expédition correspondante
curl -H "Authorization: Bearer $TOKEN" -F file=@bon.pdf localhost:8080/api/delivery-notes/extract
```

## Tests

```sh
cd services/shipment-service && ./mvnw verify
cd services/ingestion-service && ./mvnw verify
cd services/ai-service && ./mvnw verify    # API Claude simulée par WireMock, aucune clé nécessaire
```

Les tests d'intégration démarrent PostgreSQL et Kafka avec Testcontainers : Docker doit tourner.

## État du projet

Fait : création et liste des expéditions, ingestion des scans, consommation Kafka idempotente avec topic d'erreurs, extraction de bons de livraison avec l'API Claude (`ai-service`), CI.

À venir : import par lot (Spring Batch), front Vue 3 + TypeScript, authentification JWT, déploiement sur GCP Cloud Run.

Les statuts d'expédition et les types de scan sont provisoires.
