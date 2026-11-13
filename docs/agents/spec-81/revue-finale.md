# Revue finale — spec #81

Revue `code-review` medium (opus) depuis `220499d`. Passe 1 : doc, proxy, nginx (points R1 à R6). Passe 2 : code front, shipment-service, déploiement (R7 à R15).

## Points

| # | Fichier:ligne | Défaut | Correction attendue |
|---|---|---|---|
| R1 | `.claude/rules/architecture.md:50` | Dit encore que le front appelle « directement » `shipment-service` et `ingestion-service`, ce qui contredit la ligne 52 (« même origine, pas de CORS »). Un agent pourrait mettre des URL absolues ou ajouter du CORS. | Le front joint ces deux services par l'origine du front (nginx sur Cloud Run, `proxy.conf.json` en local), avec des chemins relatifs. |
| R2 | `frontend/nginx/security-headers.conf:2`, `architecture.md:52` | La CSP dite « stricte » contient `style-src 'self' 'unsafe-inline'`. | Selon la décision de Michael (voir « Décisions ») : retirer `'unsafe-inline'`, ou écrire l'exception et sa raison dans la décision. |
| R3 | `frontend/proxy.conf.json:2` | `"/api/scans"` est un préfixe en local, alors que nginx applique `^/api/scans(/|$)` (`default.conf.template:27`). `/api/scans-summary` partirait vers ingestion sous `ng serve` et vers shipment en ligne. | Clé de proxy à expression régulière équivalente `^/api/scans(/|$)` (vérifier que le proxy de la CLI Angular l'accepte), et la limite exacte écrite dans la doc à la place de « `/api/scans…` ». |
| R4 | `.claude/rules/architecture.md:53`, `frontend/package.json:32-39` | Dépendances de dev hors de la liste validée : `jsdom`, `prettier`, `typescript-eslint`, `@eslint/js` (ajoutées par la CLI Angular). | Selon la décision de Michael : les ajouter à la liste validée, ou les retirer. |
| R5 | `.claude/rules/architecture.md:52` | Le jeton est gardé dans `localStorage`, mais ce que ce choix expose n'est pas écrit : un script injecté lit le jeton, valable jusqu'à 12 h. | Selon la décision de Michael : écrire le risque accepté et ce qui le borne (CSP `script-src 'self'`, `connect-src 'self'`), ou changer de stockage. |
| R6 | `frontend/nginx/default.conf.template:15` | Le `include security-headers.conf` posé au niveau `server` ne sert à rien : chaque `location` le répète, et nginx ne transmet pas les `add_header` du `server` à une `location` qui a les siens. Une future `location` qui oublie l'`include` perdrait la CSP sans bruit. | Supprimer l'`include` de la ligne 15 et dire dans un commentaire que chaque `location` doit l'inclure. |
| R7 | `frontend/src/app/features/shipments/shipment.service.ts:17`, `services/shipment-service/.../infrastructure/web/CreateShipmentRequest.java:10` | La référence n'accepte que `@NotBlank @Size(max=50)` : `LOT/42` est créée (201), mais `GET /api/shipments/LOT%2F42` est refusé en 400 par le `StrictHttpFirewall`, donc le détail n'est jamais accessible. | Décision de Michael : `@Pattern` `[A-Za-z0-9._-]+` à la création (400 `problem+json` sinon), avec la même règle et le message dans le formulaire de création du front. Les références déjà en base ne changent pas. Tests serveur et front. Règle écrite dans `architecture.md`. |
| R8 | `frontend/src/app/features/admin/users/users.ts:64-92`, `users.html:34-36` | `mat-slide-toggle` bascule au clic. Après une annulation de la confirmation, ou un PATCH en erreur (409 par exemple), il reste basculé alors que le compte n'a pas changé (`[...list]` garde les mêmes objets). | Remettre l'interrupteur sur `user.active` après une annulation ou une erreur. e2e « annuler » et « 409 ». |
| R9 | `frontend/src/app/features/anomalies/anomalies.ts:43-58` | Changer d'onglet ne fait pas annuler la requête en cours : si la réponse `open=false` arrive en retard, l'onglet « Ouvertes » montre des anomalies closes. | `switchMap` sur l'onglet, ou annulation de l'abonnement précédent. Test. |
| R10 | `frontend/src/app/features/login/login.ts:49-50` (issue #92) | Le `returnUrl` est suivi quel que soit le rôle du compte qui se connecte : un SCANNER qui arrive après la session expirée d'un ADMIN tombe sur « Accès refusé ». | Décision de Michael : suivre `returnUrl` seulement si le rôle de la nouvelle session y a accès, sinon `homeUrl(session)`. Test. Fermer #92 avec la fusion. |
| R11 | `frontend/src/app/core/auth/auth.service.ts:49-53`, `login.ts:47-51` | Après un 200, si `decodeToken` rend null : le jeton reste stocké, la session reste null, on est renvoyé à `/login` et `sending` reste à true. Le bouton reste désactivé, sans message. | Si la session est null après la connexion : effacer le stockage, afficher une erreur, remettre `sending` à false. Test. |
| R12 | `frontend/src/app/features/scan/scan-import.ts:10-16` | `URL.revokeObjectURL` est appelé aussitôt après `link.click()` : dans Firefox ou Safari, le téléchargement peut échouer ou sortir vide. | Révoquer dans un `setTimeout`. |
| R13 | `frontend/src/app/features/scan/import-report.ts:22-23`, `frontend/src/app/features/delivery-notes/file-check.ts` | Le client compare la taille du fichier seul au plafond, mais le serveur plafonne toute la requête multipart : un fichier juste sous la limite est accepté à l'écran, puis refusé en 413. | Garder une marge côté client (par exemple 16 Kio sous le plafond de la requête). Test aux bornes. |
| R14 | `frontend/src/app/features/admin/dlt/dlt.ts:50-58` | Quand on reprend un « tout rejouer » déjà terminé, la liste du DLT est lue deux fois : une par le constructeur, une par l'`effect`. | Rafraîchir seulement au passage de « en cours » à « terminé ». |
| R15 | `frontend/src/app/features/shipments/shipment-detail.ts:23-45` | La référence vient de `snapshot.paramMap`, lue une seule fois : quand le composant est réutilisé pour une autre référence, l'ancienne expédition reste affichée. | Dériver la référence de `paramMap` (`toSignal` ou `switchMap`). |

## Décisions (Michael)

Décidé par Michael le 2026-11-13 :
- R2 : garder `'unsafe-inline'` sur `style-src` (styles inline d'Angular Material), écrire l'exception et sa raison dans la décision, et vérifier que tout le reste de la CSP est strict (`default-src 'self'`, `script-src 'self'` sans `unsafe-inline` ni `unsafe-eval`, `object-src 'none'`, `base-uri 'self'`, `frame-ancestors 'none'`, `form-action 'self'`, `connect-src 'self'`). Ce qui manque est ajouté et vérifié par l'e2e de l'image.
- R4 : `jsdom`, `prettier`, `typescript-eslint`, `@eslint/js` validés (dev), à ajouter à la liste.
- R5 : le jeton reste dans `localStorage`. Écrire le risque accepté (un script injecté lit le jeton, valable 12 h au plus) et ce qui le borne (CSP `script-src 'self'`, `connect-src 'self'`, aucun script tiers).
- R7 : on restreint les caractères de la référence à `[A-Za-z0-9._-]+`.
- R10 (#92) : à corriger.

## Lots de correction

Un implémenteur par lot, l'un après l'autre, sur la branche d'intégration à jour.

| Lot | Points | Fichiers | Statut | Commit |
|---|---|---|---|---|
| A | R1, R2, R4, R5 | `architecture.md`, `frontend/nginx/security-headers.conf`, e2e de l'image | à faire | |
| B | R3, R6 | `frontend/proxy.conf.json`, `frontend/nginx/default.conf.template`, doc du découpage | à faire | |
| C | R7, R15 | shipment-service (création), `features/shipments/` | à faire | |
| D | R8, R10, R11, R14 | `features/admin/`, `features/login/`, `core/auth/` | à faire | |
| E | R9, R12, R13 | `features/anomalies/`, `features/scan/`, `features/delivery-notes/` | à faire | |
