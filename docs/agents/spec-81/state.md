# État — spec #81 : interface web Angular (trois rôles, même origine par proxy)

STATUT: en cours
<!-- en cours | décision attendue | terminé. Lu par orchestrate.sh : garder « STATUT: » en début de ligne. -->

Budget (Michael, 2026-11-13) : nouveaux tickets ou lots jusqu'à 180k de contexte (pas 150k) ; passage de main à 190k.

Spec : issue #81. Branche d'intégration : `spec/81-interface-web` (repo principal). Parallèle : 2.

Ce fichier est relu par chaque orchestrateur neuf : il décrit l'état présent, pas l'histoire (l'histoire est dans `git log`). Moins de 6 ko.

## Tickets

| Issue | Titre court | Bloqué par | Modèle / effort | Statut | Slot / worktree / branche | Commit |
|---|---|---|---|---|---|---|
| #82 | shipment-service : détail d'une expédition, liste des comptes | — | sonnet / medium | fusionné | | 6d48c14 |
| #83 | front : amorçage, dépendances, outillage, CI, doc | — | sonnet / medium | fusionné | | 4418264 |
| #84 | front : authentification, erreurs HTTP, navigation par rôle | #83 | sonnet / medium | fusionné | | 44e1318 |
| #85 | front : expéditions (liste, détail, création) | #84 | sonnet / medium | fusionné | | bca4f30 |
| #86 | front : bons de livraison et anomalies | #85 | sonnet / medium | fusionné | | cd9ba0e |
| #87 | front : comptes et DLT (ADMIN) | #84 | sonnet / medium | fusionné | | 6d9bba9 |
| #88 | front : scan et import CSV (SCANNER) | #84 | sonnet / medium | fusionné | | 1799e18 |
| #89 | front : image nginx, CSP, vérifiée en CI | #84 | sonnet / medium | fusionné | | 0a646ab |
| #90 | déploiement : front sur Cloud Run, fumée | #89, #82 | sonnet / medium | fusionné | | 2a60c80 |

Statuts : `à faire`, `en cours`, `partiel`, `décision attendue`, `fusionné`.

Parallélisme : #82 et #83 d'abord ; après #84, #85, #87, #88 et #89 sont indépendants (chaque écran n'édite que son dossier et son `<fonctionnalité>.routes.ts`). Fichiers partagés : `frontend/package.json` et lock (#83 seul ; les autres n'ajoutent aucune dépendance), `app.routes.ts` (#84 seul), `ci.yml` (#83, #89), `environnement.md` (#83, #89), `architecture.md` (#82, #90), README (#83, #90), scripts et workflows de déploiement (#90). Aucun contrat ni migration modifiés.

Vérification en ligne du front : issue #93 (`action Michael`), ouverte après la fusion de #90.

Hors tickets : #91 (setup-node épinglé par SHA, décision de Michael) fusionné en a2a89a7.

## En attente de Michael

(rien)

## Fin de spec

Tous les tickets fusionnés ; suite complète verte le 2026-11-13 (verify des trois services, e2e front 60). Revue finale en deux passes (opus) : 15 points, décisions de Michael et lots A à E dans `revue-finale.md` (statut de chaque lot là-bas). Corrections : un implémenteur par lot, l'un après l'autre (sonnet / medium).

## Lots

Une ligne par contexte d'orchestrateur : date, tickets fusionnés, contexte final.
