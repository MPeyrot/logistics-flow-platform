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
- Validé par Michael le 2026-11-03 (#14, #15, #16) : dans les trois services, `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` et leurs starters de test ; `wiremock-standalone` 3.13.2 en test dans `ingestion-service`.

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
- Claims : `iss=shipment-service`, `aud` = service visé, `roles`, `site` (SCANNER). Durées : 1 h (ADMIN, OPERATOR), 12 h (SCANNER), 5 min (jeton de service `shipment-service` → `ai-service`). Pas de refresh token ; révocation = désactivation du compte.
- `ai-service` n'accepte que `sub=shipment-service`, `aud=ai-service`. `ingestion-service` : rôle SCANNER et site du scan = claim `site`.
- `shipment-service` valide ses propres jetons avec sa clé publique : `iss`, `exp`, et `aud` égal à `shipment-service` ou `ingestion-service` (un SCANNER reçoit ainsi 403, pas 401 ; le jeton de service `aud=ai-service` est refusé). Confirmé par Michael le 2026-11-03 (#22).
- Clé privée `JWT_PRIVATE_KEY` (PEM PKCS#8) obligatoire, générée en local par `scripts/generate-jwt-key.sh` dans `.secrets/` (ignoré par git), chargée par `scripts/env.sh`. Les tests génèrent leur clé.
- `domain` et `application` n'importent pas `org.springframework.security` (règle ArchUnit).
- Limites de débit en mémoire, par instance, 429 + `Retry-After` : 20 extractions/h par utilisateur (shipment), 100/h au total (ai), blocage de 15 min après 5 échecs de connexion.
- Connexion (décidé le 2026-11-03, #23, #24) : identifiant bloqué → 429 + `Retry-After`, aussi pour un identifiant inconnu ; une connexion réussie remet le compteur à zéro ; une tentative compte comme échec dès son début ; au-delà de 10 000 identifiants suivis, les moins récemment vus sont oubliés (même bloqués). Un ADMIN peut réactiver un compte désactivé (`PATCH /api/users/{id}`, `active`).

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
- `shipment-service` appelle `ai-service` (port `DeliveryNoteReader`, adaptateur `infrastructure/ai` en `RestClient`, HTTP/1.1, lecture jusqu'à 120 s, URL dans `AI_SERVICE_URL`). `POST /api/delivery-notes/extract` rend le bon lu, son coût et l'expédition de même référence (`shipment` null si inconnue). Erreurs relayées : 415, 422 et 429 tels quels, tout le reste 502 (401 et 403 compris : erreur de configuration de notre côté). La réponse de l'ai-service est validée avant d'entrer dans `application`. Aucune expédition n'est créée ni modifiée.
