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
- Le worktree isolé part de `main` : créer sa branche depuis la branche d'intégration (`git checkout -B <branche> <intégration>`) avant tout.
- Dans un worktree d'agent, le hook rtk refuse `git` et `source scripts/env.sh` (issue #21) : utiliser `/usr/bin/git`, et `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw …` sur une seule ligne (chemin de `scripts/env.sh`).
- Lancer un service à la main (rare) : `PORT=<8090 + 10×slot + n>`, jamais 8081 ou 8082.

## Authentification (tests de shipment-service)
- Starters Spring Boot 4.1 : `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` et leurs `-test`.
- `TestcontainersConfiguration` importe `TestSecurityConfiguration` : clé RSA générée en mémoire (`jwt.private-key`) et ADMIN amorcé `test-admin` / `test-admin-password`. `TestAccounts` crée les comptes de test (`ensure`) et obtient un jeton par `POST /api/auth/token` (`adminBearer`, `operatorBearer`, `scannerBearer`).
- `POST /api/auth/token` : corps `{"username","password"}`, réponse `{"accessToken","tokenType":"Bearer","expiresAt"}`. `sub` = identifiant du compte.
- `POST /api/users` (ADMIN) : corps `{"username","password","role","site"}`, 201 + `Location`, réponse `{"id","username","role","site","active","createdAt"}`. `PATCH /api/users/{id}` : corps `{"active":false}` (désactive) ou `{"active":true}` (réactive, #25), idempotent, 200 + le compte ; `active` absent ou null : 400.
- Blocage après échecs (`LoginThrottle`, `auth.login.*`) : en mémoire, donc partagé par toutes les classes de test qui partagent le contexte Spring. Échouer la connexion avec un identifiant propre au test, jamais 5 fois avec `test-admin` ; une connexion réussie remet le compteur à zéro.

## Projet
- Branche de base des specs : `main`. Ne rien pousser sans Michael.
- Journal des décisions : `.claude/rules/architecture.md` > Décisions (dépendances Maven : à faire valider par Michael avant de les ajouter).
- Notes des agents : `docs/notes/` (index : `docs/notes/README.md`), à créer avec la première note.
- Modèle de domaine : aucun glossaire ; modèle métier dans `architecture.md` (Site, Expédition, ÉvénementDeScan, Anomalie).
- Points ouverts : label `point-ouvert`, plus `action Michael` ou `à trancher`.
- Fichier de durées : aucun.

## Tickets
- `opus / high` pour : émission des jetons, comptes et mots de passe ; la validation de jetons dans un service consommateur reste en `sonnet / medium` si la spec fixe les cas négatifs à tester (décidé le 2026-11-03) ; consommateur Kafka (idempotence, DLT, ordre), modèle du domaine et transitions de statut.
- Seuls (jamais en parallèle d'un autre ticket) : changement d'un contrat de `contracts/` (producteur et consommateur à changer ensemble), migration Flyway.
- Fichiers partagés (arête de blocage) : `contracts/*.json`, `docker-compose.yml`, `.github/workflows/ci.yml`, les `pom.xml`, `db/migration/` de `shipment-service` (réserver les numéros `V<n>__` d'avance).
- Interdits propres au projet : jamais d'appel à la vraie API Claude (chaque appel coûte) ; `domain/` n'importe ni Spring, ni JPA, ni Kafka ; aucune bibliothèque partagée entre services.
