import { Routes } from '@angular/router';

export const PROJECT_STATUTS_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Statuts' },
    loadComponent: () => import('./statuts').then((m) => m.Statuts),
  },
];
