# État — spec #76 : import CSV des scans par lot (ingestion-service, Spring Batch)

STATUT: en cours
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #76. Branche d'intégration : `spec/76-import-csv` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #77 | recherche : Spring Batch 6 sans base (note) | — | sonnet / medium | à faire | | |
| #78 | job d'import CSV, `publishAll`, rapport | #77 | sonnet / medium | à faire | | |
| #79 | route `POST /api/scans/imports`, plafonds, journal, fumée, ferme #5 | #78 | sonnet / medium | à faire | | |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Chaîne séquentielle (aucun parallélisme). Fichiers partagés : `ingestion-service/pom.xml` (#78), `application.properties`, `architecture.md`, `smoke.sh`, README (#79). Aucun contrat ni migration modifiés.

## En attente de Michael

(rien)

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
