# État — spec #13 : authentification JWT et sécurité entre services

STATUT: en cours
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #13. Branche d'intégration : `spec/13-auth-jwt` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #14 | shipment : comptes, émission JWT, routes par rôle (migration `V3__`) | — | opus / high | en cours | slot 1 / worktree agent / ticket/14-comptes-jwt | |
| #15 | ingestion : validation jetons SCANNER, contrôle du site | — | sonnet / medium | en cours | slot 2 / worktree agent / ticket/15-ingestion-jwt | |
| #16 | ai : jeton de service, plafond global | — | sonnet / medium | à faire | | |
| #17 | shipment : API des comptes, force brute | #14 | opus / high | à faire | | |
| #18 | shipment → ai : jeton de service, limite par utilisateur, README | #17, #16 | sonnet / medium | à faire | | |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Parallélisme : #14 est le seul ticket qui touche `db/migration/` ; décidé par Michael (2026-11-03) qu'il tourne à côté de #15 et #16 (services et `pom.xml` différents).

## En attente de Michael

(rien)

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
