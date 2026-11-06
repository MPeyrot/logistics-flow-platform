# État — spec #63 : déploiement à la demande sur Cloud Run

STATUT: en cours
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #63. Branche d'intégration : `spec/63-deploiement-cloud-run` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #64 | profil `gcp` : journaux JSON, `costUsd` en champ | — | sonnet / medium | fusionné | ticket/64-profil-gcp | e14df00 |
| #65 | infrastructure GCP (`infra.sh`, assistant), ferme #58 | — | opus / high | fusionné | ticket/65-infra-gcp | c68f4ac |
| #66 | script de fumée | — | sonnet / medium | fusionné | ticket/66-fumee | 0b39bdb |
| #67 | workflows `up` / `down`, images, ferme #2 | #65, #66 | sonnet / medium | fusionné | ticket/67-workflows-up-down | aae018e |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Fichiers partagés : README (#65 puis #67), les trois `pom.xml` et `ci.yml` (#67 seul). Aucun ticket n'appelle GCP ni ne déploie.

## En attente de Michael

- #68 : validation sur un vrai déploiement (assistant, `up`, fumée, `down`), après fusion de la spec dans `main`. Hors orchestration.

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.

## Fin de spec

Points : `revue-finale.md`. Revue code-review medium depuis `ca50ff3` : défauts du code antérieur seulement ; revue ciblée des fichiers de déploiement lancée en plus (opus / medium).

| Lot | Points | Statut | Branche | Commit |
|---|---|---|---|---|
| A | A1 (#69) à A5, ai-service | fusionné | fix/63-revue-lot-a | f478442 |
| B | B1 à B3, tests de shipment-service | fusionné | fix/63-revue-lot-b | 03dbf01 |
| C | C1 à C5, infra.sh et assistant | fusionné | fix/63-revue-lot-c | 30968b1 |
| D | D1 (#71), D2 (#72), D3, ai-service et profil gcp | fusionné | fix/63-revue-lot-d | db7d444 |
| E | E1 (#70) à E5, workflows et scripts | fusionné | fix/63-revue-lot-e | 410b35a |
| E2 bis | `smoke.sh` : `-H @<(…)` dans un tableau, fermé dans un pipeline en bash 3.2 (vu à la fumée locale) | en cours | worktree isolé / fix/63-smoke-procsub | |

Hors lots, en issue : #73. Après les lots (demandé par Michael) : fumée en local (`docker compose`, trois services, `smoke.sh` sur localhost) et shellcheck vide.
