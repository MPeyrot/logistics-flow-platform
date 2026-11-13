import {
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService, LOGIN_URL } from '../auth/auth.service';
import { MessageService } from '../messages/message.service';
import { describeError } from './error-messages';

/**
 * À poser (à `true`) sur une requête dont l'écran affiche lui-même l'erreur (sous un formulaire,
 * avec un compte à rebours) : l'intercepteur n'ajoute alors pas de message commun.
 */
export const HANDLES_ERRORS = new HttpContextToken<boolean>(() => false);

const isApi = (url: string) => url.startsWith('/api/');
const isLogin = (url: string) => url === LOGIN_URL;

/** Ajoute `Authorization: Bearer` aux appels `/api/`, sauf la connexion. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(AuthService).session();
  if (!session || !isApi(req.url) || isLogin(req.url)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${session.token}` } }));
};

/** 401 : retour à la connexion ; autres erreurs : message commun, sauf si l'écran s'en charge. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const messages = inject(MessageService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && isApi(req.url) && !isLogin(req.url)) {
        if (error.status === 401) {
          auth.handleUnauthorized(router.url);
        } else if (!req.context.get(HANDLES_ERRORS)) {
          messages.error(describeError(error));
        }
      }
      return throwError(() => error);
    }),
  );
};
