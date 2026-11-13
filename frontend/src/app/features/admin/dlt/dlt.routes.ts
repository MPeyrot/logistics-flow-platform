import { Routes } from '@angular/router';

export const ADMIN_DLT_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Messages en échec' },
    loadComponent: () => import('../../../shared/placeholder-page').then((m) => m.PlaceholderPage),
  },
];
