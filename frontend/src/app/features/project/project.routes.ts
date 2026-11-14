import { Routes } from '@angular/router';
import { ProjectShell } from './project-shell';

// Espace public : aucune garde, aucun appel à l'API. Chaque page charge son propre
// `<page>.routes.ts` : les tickets suivants n'éditent que le fichier de leur page.
export const PROJECT_ROUTES: Routes = [
  {
    path: '',
    component: ProjectShell,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadChildren: () => import('./overview/overview.routes').then((m) => m.PROJECT_OVERVIEW_ROUTES),
      },
      {
        path: 'architecture',
        loadChildren: () =>
          import('./architecture/architecture.routes').then((m) => m.PROJECT_ARCHITECTURE_ROUTES),
      },
      {
        path: 'statuts',
        loadChildren: () => import('./statuts/statuts.routes').then((m) => m.PROJECT_STATUTS_ROUTES),
      },
      {
        path: 'decisions',
        loadChildren: () => import('./decisions/decisions.routes').then((m) => m.PROJECT_DECISIONS_ROUTES),
      },
      {
        path: 'ecrans',
        loadChildren: () => import('./ecrans/ecrans.routes').then((m) => m.PROJECT_ECRANS_ROUTES),
      },
    ],
  },
];
