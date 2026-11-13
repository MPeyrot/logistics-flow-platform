import { Routes } from '@angular/router';

export const DELIVERY_NOTES_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Bons de livraison' },
    loadComponent: () => import('./delivery-notes').then((m) => m.DeliveryNotes),
  },
];
