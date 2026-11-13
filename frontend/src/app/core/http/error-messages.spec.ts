import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { describeError, retryAfterSeconds } from './error-messages';

const err = (status: number, error?: unknown, headers?: Record<string, string>) =>
  new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers) });

describe('describeError', () => {
  it.each([400, 404, 409])('%i : affiche le detail du problem+json', (status) => {
    expect(describeError(err(status, { detail: 'Référence déjà utilisée' }))).toBe(
      'Référence déjà utilisée',
    );
  });

  it('400 sans detail : message générique', () => {
    expect(describeError(err(400, 'texte'))).toBe('Requête invalide.');
  });

  it('403 : Accès refusé', () => {
    expect(describeError(err(403))).toBe('Accès refusé');
  });

  it('413, 502, 503 et réseau : un message par cas', () => {
    const messages = [413, 502, 503, 0].map((s) => describeError(err(s)));
    expect(new Set(messages).size).toBe(4);
    expect(messages[0]).toContain('trop volumineux');
    expect(messages[3]).toContain('injoignable');
  });

  it('429 : mentionne le délai de Retry-After', () => {
    expect(describeError(err(429, null, { 'Retry-After': '30' }))).toContain('30 s');
  });
});

describe('retryAfterSeconds', () => {
  it('lit un entier de secondes', () => {
    expect(retryAfterSeconds(err(429, null, { 'Retry-After': '900' }))).toBe(900);
  });

  it.each([undefined, 'abc', '0', '-5', 'Wed, 21 Oct 2026 07:28:00 GMT'])('rend null pour %j', (v) => {
    expect(retryAfterSeconds(err(429, null, v === undefined ? undefined : { 'Retry-After': v }))).toBeNull();
  });
});
