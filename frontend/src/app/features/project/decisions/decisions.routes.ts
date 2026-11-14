import { Routes } from '@angular/router';

export const PROJECT_DECISIONS_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Décisions' },
    loadComponent: () => import('./decisions').then((m) => m.Decisions),
  },
];
