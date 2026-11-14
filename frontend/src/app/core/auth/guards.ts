import { inject } from '@angular/core';
import { CanActivateFn, Route, Router } from '@angular/router';
import { Role } from '../labels';
import { AuthService, Session } from './auth.service';

/** Page d'arrivée d'un compte : Tableau de bord pour ADMIN et OPERATOR, Scan pour SCANNER. */
export function homeUrl(session: Session): string {
  return session.roles.includes('ADMIN') || session.roles.includes('OPERATOR') ? '/dashboard' : '/scan';
}

/** Le compte peut-il ouvrir cette URL ? Selon `data.roles` de la route correspondante. */
export function canAccess(router: Router, session: Session, url: string): boolean {
  const segments = router.parseUrl(url).root.children['primary']?.segments ?? [];
  const path = segments.map((s) => s.path).join('/');
  const route: Route | undefined = router.config.find(
    (r) => r.path && (path === r.path || path.startsWith(`${r.path}/`)),
  );
  const roles = route?.data?.['roles'] as Role[] | undefined;
  return !roles || session.roles.some((r) => roles.includes(r));
}

/** Connexion obligatoire ; sinon `/login?returnUrl=<url demandée>`. */
export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  if (inject(AuthService).session()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Rôle obligatoire (l'un des rôles donnés) ; sinon la page « Accès refusé ». */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const router = inject(Router);
    const auth = inject(AuthService);
    if (!auth.session()) {
      return router.createUrlTree(['/login']);
    }
    return auth.hasRole(...roles) ? true : router.createUrlTree(['/access-denied']);
  };
}

/** Racine et URL inconnues : page d'arrivée du compte, ou présentation publique du projet. */
export const homeGuard: CanActivateFn = () => {
  const router = inject(Router);
  const session = inject(AuthService).session();
  return router.createUrlTree([session ? homeUrl(session) : '/projet']);
};
