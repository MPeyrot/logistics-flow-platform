import { Routes } from '@angular/router';

export const SCAN_ROUTES: Routes = [
  {
    path: '',
    data: { title: 'Scan' },
    loadComponent: () => import('./scan').then((m) => m.Scan),
  },
  {
    path: 'import',
    data: { title: 'Import CSV' },
    loadComponent: () => import('./scan-import').then((m) => m.ScanImport),
  },
];
