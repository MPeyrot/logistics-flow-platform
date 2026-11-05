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

Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA / Hibernate, Flyway, PostgreSQL 17, Apache Kafka, Maven, JUnit 5, Testcontainers, ArchUnit, Docker Compose, GitHub Actions.

## Lancer en local

Prérequis : JDK 21, Docker ; `curl` et `jq` pour le parcours ci-dessous.

```sh
scripts/generate-jwt-key.sh    # une fois : clé de signature des JWT dans .secrets/ (ignoré par git)
source scripts/env.sh          # JAVA_HOME sur openjdk@21 (macOS/Homebrew ; adapter ailleurs) et JWT_PRIVATE_KEY
docker compose up -d --wait    # PostgreSQL et Kafka

# premier ADMIN, créé au démarrage s'il n'en existe aucun (mot de passe : 12 caractères au moins)
(cd services/shipment-service && BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD=... ./mvnw spring-boot:run) &
(cd services/ingestion-service && ./mvnw spring-boot:run) &
(cd services/ai-service && ANTHROPIC_API_KEY=... ./mvnw spring-boot:run) &   # clé d'API Claude, jamais dans le dépôt
```

Variables d'environnement liées à l'authentification :

| Variable | Service | Rôle |
|---|---|---|
| `JWT_PRIVATE_KEY` | shipment-service | clé privée RSA (PEM) qui signe les jetons ; obligatoire. `scripts/env.sh` la charge depuis `.secrets/` |
| `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` | shipment-service | premier ADMIN, créé au démarrage s'il n'en existe aucun |
| `SHIPMENT_SERVICE_URL` | ingestion-service, ai-service | où lire la clé publique (`/.well-known/jwks.json`) ; défaut `http://localhost:8080` |
| `AI_SERVICE_URL` | shipment-service | où joindre ai-service ; défaut `http://localhost:8082` |

Parcours complet (les rôles et les jetons sont décrits dans `.claude/rules/architecture.md`) :

```sh
# 1. jeton ADMIN (valable 1 h)
ADMIN=$(curl -s -X POST localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"..."}' | jq -r .accessToken)

# 2. créer un compte SCANNER (rattaché à un site) et un compte OPERATOR
curl -X POST localhost:8080/api/users -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"username":"scanner-nantes","password":"un mot de passe long","role":"SCANNER","site":"Nantes"}'
curl -X POST localhost:8080/api/users -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"username":"operateur","password":"un mot de passe long","role":"OPERATOR"}'

# 3. jeton SCANNER (valable 12 h) et jeton OPERATOR (1 h)
SCANNER=$(curl -s -X POST localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"scanner-nantes","password":"un mot de passe long"}' | jq -r .accessToken)
OPERATOR=$(curl -s -X POST localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"operateur","password":"un mot de passe long"}' | jq -r .accessToken)

# 4. créer une expédition (OPERATOR). expectedDeliveryAt (ISO-8601) est obligatoire et dans le futur, sinon 400.
#    Ici dans trois jours (macOS ; sous Linux : date -u -d '+3 days' +%Y-%m-%dT%H:%M:%SZ).
DUE=$(date -u -v+3d +%Y-%m-%dT%H:%M:%SZ)
curl -X POST localhost:8080/api/shipments -H "Authorization: Bearer $OPERATOR" -H 'Content-Type: application/json' \
  -d '{"reference":"REF-1001","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"'"$DUE"'"}'

# 5. envoyer un scan (SCANNER ; le site doit être celui du compte ; traité en asynchrone via Kafka)
curl -X POST localhost:8081/api/scans -H "Authorization: Bearer $SCANNER" -H 'Content-Type: application/json' \
  -d '{"shipmentReference":"REF-1001","type":"PICKED_UP","site":"Nantes"}'

# 6. le statut est passé de CREATED à IN_TRANSIT
curl -H "Authorization: Bearer $OPERATOR" localhost:8080/api/shipments

# 7. lire un bon de livraison (PDF ou image) : données extraites, coût, expédition correspondante.
#    Chaque appel coûte un appel à l'API Claude : 20 par heure et par utilisateur (429 avec Retry-After au-delà).
curl -H "Authorization: Bearer $OPERATOR" -F file=@bon.pdf localhost:8080/api/delivery-notes/extract

# 8. anomalies (ADMIN, OPERATOR) : open=true (ouvertes), open=false (closes), sans paramètre (toutes),
#    les plus récemment détectées d'abord ; open invalide : 400.
curl -H "Authorization: Bearer $OPERATOR" 'localhost:8080/api/anomalies?open=true'
#    les anomalies ouvertes d'une expédition sont aussi dans openAnomalies de GET /api/shipments
```

Accès aux routes de `shipment-service` (sans jeton : 401 ; rôle non permis : 403) :

| Route | ADMIN | OPERATOR | SCANNER |
| --- | --- | --- | --- |
| `GET /api/shipments`, `POST /api/shipments` | oui | oui | 403 |
| `GET /api/anomalies` | oui | oui | 403 |
| `POST /api/delivery-notes/extract` | oui | oui | 403 |
| `POST /api/users`, `PATCH /api/users/{id}` | oui | 403 | 403 |

## Tests

```sh
cd services/shipment-service && ./mvnw verify
cd services/ingestion-service && ./mvnw verify
cd services/ai-service && ./mvnw verify    # API Claude simulée par WireMock, aucune clé nécessaire
```

Les tests d'intégration démarrent PostgreSQL et Kafka avec Testcontainers : Docker doit tourner.

## État du projet

Fait : création et liste des expéditions, authentification JWT (comptes, rôles, jetons RS256), ingestion des scans, consommation Kafka idempotente avec topic d'erreurs, extraction de bons de livraison avec l'API Claude (`ai-service`), CI.

À venir : import par lot (Spring Batch), front Vue 3 + TypeScript, déploiement sur GCP Cloud Run.

Statuts d'une expédition et transitions permises (tout autre scan est refusé et part dans `scan-events.DLT`) :

| Statut | Scan | Nouveau statut |
| --- | --- | --- |
| CREATED | PICKED_UP | IN_TRANSIT |
| IN_TRANSIT | ARRIVED_AT_SITE | IN_TRANSIT |
| IN_TRANSIT | OUT_FOR_DELIVERY | OUT_FOR_DELIVERY |
| OUT_FOR_DELIVERY | DELIVERED | DELIVERED |
| OUT_FOR_DELIVERY | DELIVERY_FAILED | DELIVERY_FAILED |
| DELIVERY_FAILED | ARRIVED_AT_SITE | IN_TRANSIT |

DELIVERED est terminal. Le contrat Kafka `scan-event.v1` ne porte encore que PICKED_UP, ARRIVED_AT_SITE et DELIVERED : en attendant `scan-event.v2`, une expédition ne peut pas encore être livrée par un scan.
