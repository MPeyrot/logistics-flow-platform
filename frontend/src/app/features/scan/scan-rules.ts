import type { ScanType } from '../../core/labels';

export const REASON_MAX = 200;

/** Longueur en points de code, comme le contrat (`reason` ≤ 200 points de code). */
export function reasonCount(reason: string): number {
  return Array.from(reason).length;
}

/** Le motif n'existe qu'avec DELIVERY_FAILED. */
export function allowsReason(type: ScanType): boolean {
  return type === 'DELIVERY_FAILED';
}

/** Message d'erreur du motif, ou null. Un motif vide est permis ; sans objet hors DELIVERY_FAILED. */
export function reasonError(type: ScanType, reason: string): string | null {
  if (!allowsReason(type) || reason === '') {
    return null;
  }
  // `\S` ECMAScript, comme le contrat : l'espace insécable est un blanc.
  if (!/\S/.test(reason)) {
    return 'Le motif ne peut pas être fait que de blancs.';
  }
  if (reasonCount(reason) > REASON_MAX) {
    return `Le motif ne peut pas dépasser ${REASON_MAX} caractères.`;
  }
  return null;
}

/** Motif à envoyer : absent hors DELIVERY_FAILED ou s'il est vide. */
export function reasonToSend(type: ScanType, reason: string): string | undefined {
  return allowsReason(type) && reason !== '' ? reason : undefined;
}
