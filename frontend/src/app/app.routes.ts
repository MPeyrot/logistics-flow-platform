import { Routes } from '@angular/router';
import { authGuard, homeGuard, roleGuard } from './core/auth/guards';

// Toutes les routes de la spec sont déclarées ici, chacune en chargement différé vers le
// `<fonctionnalité>.routes.ts` de son écran : les tickets d'écran n'éditent plus ce fichier.
const staff = [authGuard, roleGuard('ADMIN', 'OPERATOR')];
const admin = [authGuard, roleGuard('ADMIN')];
const scanner = [authGuard, roleGuard('SCANNER')];

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
    path: 'shipments',
    canActivate: staff,
    loadChildren: () =>
      import('./features/shipments/shipments.routes').then((m) => m.SHIPMENTS_ROUTES),
  },
  {
    path: 'anomalies',
    canActivate: staff,
    loadChildren: () =>
      import('./features/anomalies/anomalies.routes').then((m) => m.ANOMALIES_ROUTES),
  },
  {
    path: 'delivery-notes',
    canActivate: staff,
    loadChildren: () =>
      import('./features/delivery-notes/delivery-notes.routes').then((m) => m.DELIVERY_NOTES_ROUTES),
  },
  {
    path: 'admin/users',
    canActivate: admin,
    loadChildren: () =>
      import('./features/admin/users/users.routes').then((m) => m.ADMIN_USERS_ROUTES),
  },
  {
    path: 'admin/dlt',
    canActivate: admin,
    loadChildren: () => import('./features/admin/dlt/dlt.routes').then((m) => m.ADMIN_DLT_ROUTES),
  },
  {
    path: 'scan',
    canActivate: scanner,
    loadChildren: () => import('./features/scan/scan.routes').then((m) => m.SCAN_ROUTES),
  },
  { path: '**', canActivate: [homeGuard], children: [] },
];
