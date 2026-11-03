# Environnement des agents — Logistics Flow Platform

Lu par l'orchestrateur, les implémenteurs et `/preparer-spec` (skills globaux `~/.claude/skills/orchestrer-spec` et `preparer-spec`). Seulement ce qui est propre à ce projet.

## Vérifications
- Toujours `source scripts/env.sh` d'abord, dans le même appel que `./mvnw` (le shell ne garde pas l'environnement d'un appel à l'autre).
- Rapides (après chaque fusion) : `./mvnw -B -ntp -q verify` dans chaque service touché par le ticket (`services/<service>`). Un test : `./mvnw -B -ntp test -Dtest=<Classe>`.
- Finales (implémenteur) : `./mvnw -B -ntp verify` dans les trois services (compilation, tests, règles ArchUnit). Pas de suite e2e.
- Suite complète (orchestrateur, au passage de main) : `verify` des trois services.

## Préparer un worktree
- Rien à copier : aucun secret n'est nécessaire aux tests (WireMock pour l'API Claude, Testcontainers pour PostgreSQL et Kafka).
- Docker doit tourner (OrbStack). Les tests ne fixent aucun port (Testcontainers choisit les siens) : deux implémenteurs en parallèle ne se gênent pas, mais chacun démarre ses conteneurs. Pas de `docker compose up` dans un worktree (ports 5432 et 9092 fixes, partagés).
- Lancer un service à la main (rare) : `PORT=<8090 + 10×slot + n>`, jamais 8081 ou 8082.

## Projet
- Branche de base des specs : `main`. Ne rien pousser sans Michael.
- Journal des décisions : `.claude/rules/architecture.md` > Décisions (dépendances Maven : à faire valider par Michael avant de les ajouter).
- Notes des agents : `docs/notes/` (index : `docs/notes/README.md`), à créer avec la première note.
- Modèle de domaine : aucun glossaire ; modèle métier dans `architecture.md` (Site, Expédition, ÉvénementDeScan, Anomalie).
- Points ouverts : label `point-ouvert`, plus `action Michael` ou `à trancher`.
- Fichier de durées : aucun.

## Tickets
- `opus / high` pour : authentification JWT et sécurité entre services, consommateur Kafka (idempotence, DLT, ordre), modèle du domaine et transitions de statut.
- Seuls (jamais en parallèle d'un autre ticket) : changement d'un contrat de `contracts/` (producteur et consommateur à changer ensemble), migration Flyway.
- Fichiers partagés (arête de blocage) : `contracts/*.json`, `docker-compose.yml`, `.github/workflows/ci.yml`, les `pom.xml`, `db/migration/` de `shipment-service` (réserver les numéros `V<n>__` d'avance).
- Interdits propres au projet : jamais d'appel à la vraie API Claude (chaque appel coûte) ; `domain/` n'importe ni Spring, ni JPA, ni Kafka ; aucune bibliothèque partagée entre services.
