# État — spec #13 : authentification JWT et sécurité entre services

STATUT: terminé
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #13. Branche d'intégration : `spec/13-auth-jwt` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #14 | shipment : comptes, émission JWT, routes par rôle (migration `V3__`) | — | opus / high | fusionné | | 520ef16 |
| #15 | ingestion : validation jetons SCANNER, contrôle du site | — | sonnet / medium | fusionné | | 331804a |
| #16 | ai : jeton de service, plafond global | — | sonnet / medium | fusionné | | b69df23 |
| #17 | shipment : API des comptes, force brute | #14 | opus / high | fusionné | | 516f8a8 |
| #18 | shipment → ai : jeton de service, limite par utilisateur, README | #17, #16 | sonnet / medium | fusionné | | a21ae11 |
| #25 | shipment : réactivation d'un compte (décision #24) | #18 | opus / high | fusionné | | 119c226 |
| #26 | corrections de la revue de fin de spec | #25 | opus / high | fusionné | | 3ad8bae |
| #31 | suites de la revue : bloqués en dernier, quota si ai injoignable, test d'amorçage (#28, #29, #30) | #26 | opus / high | fusionné | | c200af4 |
| #32 | connexion : taille de l'identifiant bornée | #31 | opus / high | fusionné | | 6d356c4 |
| #6, #7 | hors spec : version Kafka alignée, actions CI à jour | — | sonnet / medium | fusionné | | bb6a4c8 |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Parallélisme : #14 est le seul ticket qui touche `db/migration/` ; décidé par Michael (2026-11-03) qu'il tourne à côté de #15 et #16 (services et `pom.xml` différents).

## En attente de Michael

(rien)

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
- 2026-11-03 : #15, #16, #14, #17, #18, #25 (réactivation, décision #24), #26 (revue de fin de spec) fusionnés ; verify des trois services vert (113, 22, 45 tests). Contexte final : 116k. Points ouverts : #21, #28, #29, #30.
- 2026-11-04 : #31 (suites de la revue : #28, #29, #30) fusionné ; verify vert (123, 22, 45 tests). Contexte final : 128k.
- 2026-11-04 : #32, et hors spec #6 et #7, fusionnés ; verify vert (124, 22, 45 tests). Contexte final : 246k.
