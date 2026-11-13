import { Routes } from '@angular/router';

export const ANOMALIES_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Anomalies' },
    loadComponent: () => import('./anomalies').then((m) => m.Anomalies),
  },
];
