import { Routes } from '@angular/router';
import { Role } from './core/labels';
import { authGuard, homeGuard, roleGuard } from './core/auth/guards';

// Toutes les routes de la spec sont déclarées ici, chacune en chargement différé vers le
// `<fonctionnalité>.routes.ts` de son écran : les tickets d'écran n'éditent plus ce fichier.
// `data.roles` : les rôles admis, lus aussi par la connexion (`canAccess`) pour ne suivre un
// `returnUrl` que si le compte qui se connecte y a accès.
const staff = [authGuard, roleGuard('ADMIN', 'OPERATOR')];
const admin = [authGuard, roleGuard('ADMIN')];
const scanner = [authGuard, roleGuard('SCANNER')];
const STAFF: Role[] = ['ADMIN', 'OPERATOR'];

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeGuard], children: [] },
  { path: 'login', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  {
    path: 'access-denied',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/access-denied/access-denied').then((m) => m.AccessDenied),
  },
  {
    path: 'dashboard',
    canActivate: staff,
    data: { roles: STAFF },
    loadChildren: () =>
      import('./features/dashboard/dashboard.routes').then((m) => m.DASHBOARD_ROUTES),
  },
  {
    path: 'shipments',
    canActivate: staff,
    data: { roles: STAFF },
    loadChildren: () =>
      import('./features/shipments/shipments.routes').then((m) => m.SHIPMENTS_ROUTES),
  },
  {
    path: 'anomalies',
    canActivate: staff,
    data: { roles: STAFF },
    loadChildren: () =>
      import('./features/anomalies/anomalies.routes').then((m) => m.ANOMALIES_ROUTES),
  },
  {
    path: 'delivery-notes',
    canActivate: staff,
    data: { roles: STAFF },
    loadChildren: () =>
      import('./features/delivery-notes/delivery-notes.routes').then((m) => m.DELIVERY_NOTES_ROUTES),
  },
  {
    path: 'admin/users',
    canActivate: admin,
    data: { roles: ['ADMIN'] },
    loadChildren: () =>
      import('./features/admin/users/users.routes').then((m) => m.ADMIN_USERS_ROUTES),
  },
  {
    path: 'admin/dlt',
    canActivate: admin,
    data: { roles: ['ADMIN'] },
    loadChildren: () => import('./features/admin/dlt/dlt.routes').then((m) => m.ADMIN_DLT_ROUTES),
  },
  {
    path: 'scan',
    canActivate: scanner,
    data: { roles: ['SCANNER'] },
    loadChildren: () => import('./features/scan/scan.routes').then((m) => m.SCAN_ROUTES),
  },
  { path: '**', canActivate: [homeGuard], children: [] },
];
