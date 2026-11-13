import { Routes } from '@angular/router';

export const SHIPMENTS_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Expéditions' },
    loadComponent: () => import('./shipment-list').then((m) => m.ShipmentList),
  },
  {
    path: ':reference',
    data: { title: 'Détail d’une expédition' },
    loadComponent: () => import('./shipment-detail').then((m) => m.ShipmentDetailPage),
  },
];
