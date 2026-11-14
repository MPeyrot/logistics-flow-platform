# État — spec #96 : vitrine du projet dans le front Angular

STATUT: en cours
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #96. Branche d'intégration : `spec/96-vitrine` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #98 | Back-end : scan ADMIN/OPERATOR, `usage` | — | opus / high | fusionné | | 706e150 |
| #99 | Tableau de bord `/dashboard` | — | sonnet / medium | fusionné | | e9d8e23 |
| #100 | Coquille `/projet` et diagramme des statuts | #99 | sonnet / medium | fusionné | | fffbbd5 |
| #101 | Boutons de scan du détail, frise, `usage` affiché | #98, #100 | sonnet / medium | en cours | 1 / (worktree isolé) / ticket/101-scan-detail | |
| #102 | `/projet/architecture` et CSP de l'image | #100 | sonnet / medium | en cours | 2 / (worktree isolé) / ticket/102-architecture-csp | |
| #103 | Chiffres du projet (`project-stats.json`) | #100 | sonnet / medium | fusionné | | 9b8132b |
| #104 | Données de démo et fumée réaliste | #98, #103 | sonnet / medium | fusionné | | e4bc113 |
| #105 | `/projet/decisions` | #100 | sonnet / medium | à faire | | |
| #106 | Captures et `/projet/ecrans` | #99, #101, #104 | sonnet / medium | à faire | | |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

## En attente de Michael

(rien ; #97, vider Neon, et #107, vérifier l'amorçage en ligne : #104 est fusionné, hors orchestration)

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
