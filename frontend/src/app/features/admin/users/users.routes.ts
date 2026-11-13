import { Routes } from '@angular/router';

export const ADMIN_USERS_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Comptes' },
    loadComponent: () => import('../../../shared/placeholder-page').then((m) => m.PlaceholderPage),
  },
];
