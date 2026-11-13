import { HttpErrorResponse } from '@angular/common/http';

/** `Retry-After` en secondes (entier positif), ou null s'il est absent ou illisible. */
export function retryAfterSeconds(error: HttpErrorResponse): number | null {
  const value = error.headers?.get('Retry-After');
  if (value === null || value === undefined || !/^\d+$/.test(value.trim())) {
    return null;
  }
  const seconds = Number(value.trim());
  return seconds > 0 ? seconds : null;
}

/** `detail` d'un corps `problem+json`, s'il y en a un. */
export function problemDetail(error: HttpErrorResponse): string | null {
  const body: unknown = error.error;
  if (typeof body === 'object' && body !== null) {
    const detail = (body as { detail?: unknown }).detail;
    if (typeof detail === 'string' && detail.trim() !== '') {
      return detail;
    }
  }
  return null;
}

/** Message français d'une erreur HTTP, un par cas. */
export function describeError(error: HttpErrorResponse): string {
  switch (error.status) {
    case 0:
      return 'Le serveur est injoignable. Vérifiez votre connexion et réessayez.';
    case 400:
      return problemDetail(error) ?? 'Requête invalide.';
    case 401:
      return 'Session expirée. Reconnectez-vous.';
    case 403:
      return 'Accès refusé';
    case 404:
      return problemDetail(error) ?? 'Ressource introuvable.';
    case 409:
      return problemDetail(error) ?? 'Conflit avec l’état actuel de la ressource.';
    case 413:
      return 'Le fichier est trop volumineux.';
    case 429: {
      const seconds = retryAfterSeconds(error);
      return seconds === null
        ? 'Trop de demandes. Réessayez dans un moment.'
        : `Trop de demandes. Réessayez dans ${seconds} s.`;
    }
    case 502:
      return 'Un service est momentanément injoignable (502). Réessayez dans un moment.';
    case 503:
      return 'Le service est indisponible (503). Réessayez dans un moment.';
    default:
      return `Une erreur est survenue (code ${error.status}).`;
  }
}
