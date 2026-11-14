import { Routes } from '@angular/router';

export const PROJECT_ECRANS_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Écrans' },
    loadComponent: () => import('./ecrans').then((m) => m.ProjectEcrans),
  },
];
