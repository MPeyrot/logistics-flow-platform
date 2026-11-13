import { Routes } from '@angular/router';

const placeholder = () => import('../../shared/placeholder-page').then((m) => m.PlaceholderPage);

export const SHIPMENTS_ROUTES: Routes = [
  { path: '', data: { title: 'Expéditions' }, loadComponent: placeholder },
  { path: ':reference', data: { title: 'Détail d’une expédition' }, loadComponent: placeholder },
];
