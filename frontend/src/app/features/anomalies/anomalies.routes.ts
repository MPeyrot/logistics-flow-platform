import { Routes } from '@angular/router';

export const ANOMALIES_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Anomalies' },
    loadComponent: () => import('../../shared/placeholder-page').then((m) => m.PlaceholderPage),
  },
];
