import { Routes } from '@angular/router';

export const PROJECT_ARCHITECTURE_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Architecture' },
    loadComponent: () => import('./architecture').then((m) => m.Architecture),
  },
];
