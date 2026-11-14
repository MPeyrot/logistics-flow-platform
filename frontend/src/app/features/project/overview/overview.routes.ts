import { Routes } from '@angular/router';

export const PROJECT_OVERVIEW_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Vue d’ensemble' },
    loadComponent: () => import('./overview').then((m) => m.Overview),
  },
];
