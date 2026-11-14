# État — spec #96 : vitrine du projet dans le front Angular

STATUT: terminé
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Spec : issue #96. Branche d'intégration : `spec/96-vitrine` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #98 | Back-end : scan ADMIN/OPERATOR, `usage` | — | opus / high | fusionné | | 706e150 |
| #99 | Tableau de bord `/dashboard` | — | sonnet / medium | fusionné | | e9d8e23 |
| #100 | Coquille `/projet` et diagramme des statuts | #99 | sonnet / medium | fusionné | | fffbbd5 |
| #101 | Boutons de scan du détail, frise, `usage` affiché | #98, #100 | sonnet / medium | fusionné | | 44f831a |
| #102 | `/projet/architecture` et CSP de l'image | #100 | sonnet / medium | fusionné | | b65a5e3 |
| #103 | Chiffres du projet (`project-stats.json`) | #100 | sonnet / medium | fusionné | | 9b8132b |
| #104 | Données de démo et fumée réaliste | #98, #103 | sonnet / medium | fusionné | | e4bc113 |
| #105 | `/projet/decisions` | #100 | sonnet / medium | fusionné | | ea3bb8e |
| #106 | Captures et `/projet/ecrans` | #99, #101, #104 | sonnet / medium | fusionné | | 96329a7 |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

## En attente de Michael

(rien.) Neon vidé le 2026-11-14 (#97 fermée). PR #111 ouverte le 2026-11-14 à la demande de Michael (#108 validée). Après sa fusion dans `main` (CI verte) : s'occuper de #107 (`deploy-up` avec `seed`, vérifier les 20 expéditions puis les 3 LATE 15 min après).

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.

- 2026-11-14 : #98 à #106 fusionnés (9 tickets) ; suite complète verte (verify ×3, e2e 118, e2e image 9). Revue finale : 10 points, lots A–C fusionnés ; e2e 118 et image 9 verts après (services inchangés depuis le verify) ; ~145k.

## Fin de spec

Sha de départ de la revue : `f7d3d26` (merge-base `main`). Revue finale (opus / medium) : faite, 10 points dans `revue-finale.md`, tous corrigés. Spec terminée le 2026-11-14 ; issue #96 laissée ouverte, rien de poussé.

| Lot | Points | Statut | Commit |
|---|---|---|---|
| A | 1–4 (tableau de bord, fausse API) | fusionné | 298df1b |
| B | 5–8 (détail et liste des expéditions) | fusionné | 3a65da0 |
| C | 9–10 (coquille, amorçage) | fusionné | e42fcbe |
