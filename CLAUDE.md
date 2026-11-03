# Logistics Flow Platform

Plateforme de suivi de flux logistiques : microservices Spring Boot (Java 21, Maven) en monorepo. Architecture et règles : `.claude/rules/architecture.md`.

## Commandes
- Toujours `source scripts/env.sh` d'abord (JAVA_HOME sur openjdk@21 ; le JDK par défaut de la machine est plus récent).
- Tests d'un service : `cd services/<service> && ./mvnw -B -ntp verify` (ce que lance la CI). Docker doit tourner : Testcontainers démarre PostgreSQL et Kafka.
- Lancer en local : `docker compose up -d --wait`, puis `./mvnw spring-boot:run` dans chaque service (voir `README.md`).
- `ai-service` : aucun test n'appelle la vraie API Claude (WireMock). Ne jamais l'appeler avec une vraie clé sans l'accord de Michael : chaque appel coûte.

## Points ouverts
Une issue GitHub par point (bug repéré, tâche, décision à prendre par Michael), avec le label `point-ouvert` ; en plus `action Michael` (à faire par lui) ou `à trancher` (décision). Les lister : `gh issue list --label point-ouvert --json number,title -q '.[] | "\(.number) \(.title)"'` ; n'ouvrir que celle qui concerne la tâche. Remplace l'ancien `.claude/rules/todo.md`.
