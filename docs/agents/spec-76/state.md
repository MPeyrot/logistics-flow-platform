# État — spec #76 : import CSV des scans par lot (ingestion-service, Spring Batch)

STATUT: en cours
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #76. Branche d'intégration : `spec/76-import-csv` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #77 | recherche : Spring Batch 6 sans base (note) | — | sonnet / medium | fusionné | | 75e94e8 |
| #78 | job d'import CSV, `publishAll`, rapport | #77 | sonnet / medium | fusionné | | 960b09f |
| #79 | route `POST /api/scans/imports`, plafonds, journal, fumée, ferme #5 | #78 | sonnet / medium | fusionné | | 25c302e |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Chaîne séquentielle (aucun parallélisme). Fichiers partagés : `ingestion-service/pom.xml` (#78), `application.properties`, `architecture.md`, `smoke.sh`, README (#79). Aucun contrat ni migration modifiés.

## En attente de Michael

(rien)

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
- 2026-11-07 : #77, #78, #79 fusionnés ; `verify` des trois services vert (73 + 241 + 70 tests) ; contexte ~75k.
- 2026-11-07 : revue finale, lots B et A fusionnés ; `verify` des trois services vert (82 + 241 + 70 tests) ; contexte ~100k. Spec terminée, rien poussé.
- 2026-11-07 : seconde revue demandée par Michael (`5163728..HEAD`) : 8 points retenus, lots C et D.

## Fin de spec

Revue finale (opus / medium) depuis `5163728` : faite, 7 points dans `docs/agents/spec-76/revue-finale.md`.

| Lot | Points | Statut | Branche | Commit |
|---|---|---|---|---|
| B | B1, B2 (structure, multipart) | fusionné | | 25ca805 |
| A | A1–A5 (lecture CSV, journal) | fusionné | | 29d2bf0 |
| C | C1–C4 (seconde revue : découpage CSV) | fusionné | | cfb51f6 |
| D | D1–D5 (seconde revue : rejets, Kafka) | en cours | ticket/76-revue-lot-d | |
