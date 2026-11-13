import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../labels';
import { AuthService, Session } from './auth.service';

/** Page d'arrivée d'un compte : Expéditions pour ADMIN et OPERATOR, Scan pour SCANNER. */
export function homeUrl(session: Session): string {
  return session.roles.includes('ADMIN') || session.roles.includes('OPERATOR') ? '/shipments' : '/scan';
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

/** Racine et URL inconnues : page d'arrivée du compte, ou connexion. */
export const homeGuard: CanActivateFn = () => {
  const router = inject(Router);
  const session = inject(AuthService).session();
  return router.createUrlTree([session ? homeUrl(session) : '/login']);
};
