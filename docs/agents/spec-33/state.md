# État — spec #33 : cycle de vie des expéditions (statuts, anomalies, DLT)

STATUT: en cours
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #33. Branche d'intégration : `spec/33-cycle-vie-expeditions` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #34 | migration `V4__` du cycle de vie (**seul**) | — | sonnet / medium | fusionné | ticket/34-migration-v4 | 5aa40ba |
| #35 | statuts et transitions définitifs (domaine) | #34 | opus / high | fusionné | ticket/35-statuts-transitions | ccab8f7 |
| #36 | backoff exponentiel « expédition inconnue » | #34 | opus / high | fusionné | ticket/36-backoff-inconnue | ac5fa8d |
| #37 | contrat `scan-event.v2` + motif (**seul**) | #34, #35 | opus / high | fusionné | ticket/37-scan-event-v2 | 7f50b3f |
| #38 | `expectedDeliveryAt` à la création | #34, #35 | opus / high | fusionné | ticket/38-expected-delivery-at | 203ae7f |
| #39 | détection et clôture des anomalies | #35, #38 | opus / high | fusionné | ticket/39-anomalies | 7113cd3 |
| #40 | lecture des anomalies (API), ferme #1 | #39 | sonnet / medium | fusionné | ticket/40-lecture-anomalies | 5554024 |
| #41 | lecture et rejeu du DLT (ADMIN), ferme #4 | #34, #36 | opus / high | fusionné | ticket/41-rejeu-dlt | 873c2b5 |
| #45 | `expectedDeliveryAt` : ISO-8601 seulement (décidé par Michael) | #38 | sonnet / medium | fusionné | ticket/45-iso-8601 | e991891 |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Parallélisme : #34 et #37 tournent seuls (migration, contrat). #35 et #36 peuvent tourner ensemble (domaine / configuration Kafka). #40 et #41 touchent tous deux `SecurityConfig` (fusion simple, pas d'arête) ; #36 et #41 partagent `KafkaConsumerConfig` (arête posée).

## En attente de Michael

(rien)

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
- 2026-11-05 : #34, #35, #36, #38, #39, #41, #40 fusionnés ; verify des trois services vert à 5554024. Arrêt demandé par Michael. Reste #45 puis #37 (seul, en dernier).

## Fin de spec

- Revue finale faite le 2026-11-06 (code-review medium depuis 9d4f959) : 11 points dans `revue-finale.md` (9 de la revue + #50, #51), décisions de Michael incluses.
- Lots, un à la fois, dans cet ordre : D (point 1, `V5__`), A (2, 3, 4), C (8 à 11), B (5, 6, 7, `V6__`).

| Lot | Modèle / effort | Statut | Branche | Commit |
|---|---|---|---|---|
| D | opus / high | fusionné | revue/33-lot-d | 7d6c8fa |
| A | sonnet / medium | fusionné | revue/33-lot-a | 88bf97b |
| C | sonnet / medium | fusionné | revue/33-lot-c | fc00818 |
| B | opus / high | en cours | revue/33-lot-b | |
