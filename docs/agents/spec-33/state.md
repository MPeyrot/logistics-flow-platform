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
| #37 | contrat `scan-event.v2` + motif (**seul**) | #34, #35 | opus / high | à faire | | |
| #38 | `expectedDeliveryAt` à la création | #34, #35 | opus / high | fusionné | ticket/38-expected-delivery-at | 203ae7f |
| #39 | détection et clôture des anomalies | #35, #38 | opus / high | en cours | 1 / (worktree isolé) / ticket/39-anomalies | |
| #40 | lecture des anomalies (API), ferme #1 | #39 | sonnet / medium | à faire | | |
| #41 | lecture et rejeu du DLT (ADMIN), ferme #4 | #34, #36 | opus / high | en cours | 2 / (worktree isolé) / ticket/41-rejeu-dlt | |
| #45 | `expectedDeliveryAt` : ISO-8601 seulement (décidé par Michael) | #38 | sonnet / medium | à faire | | |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Parallélisme : #34 et #37 tournent seuls (migration, contrat). #35 et #36 peuvent tourner ensemble (domaine / configuration Kafka). #40 et #41 touchent tous deux `SecurityConfig` (fusion simple, pas d'arête) ; #36 et #41 partagent `KafkaConsumerConfig` (arête posée).

## En attente de Michael

(rien)

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
