import { Routes } from '@angular/router';

const placeholder = () => import('../../shared/placeholder-page').then((m) => m.PlaceholderPage);

export const SCAN_ROUTES: Routes = [
  { path: '', data: { title: 'Scan' }, loadComponent: placeholder },
  { path: 'import', data: { title: 'Import CSV' }, loadComponent: placeholder },
];
