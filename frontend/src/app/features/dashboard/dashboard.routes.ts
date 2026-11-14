import { Routes } from '@angular/router';

export const DASHBOARD_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Tableau de bord' },
    loadComponent: () => import('./dashboard').then((m) => m.Dashboard),
  },
];
