import { describe, expect, it } from 'vitest';
import { REASON_MAX, reasonCount, reasonError, reasonToSend } from './scan-rules';

describe('motif', () => {
  it('compte des points de code, pas des unités UTF-16', () => {
    expect(reasonCount('abc')).toBe(3);
    expect(reasonCount('😀😀')).toBe(2);
  });

  it('accepte un motif absent ou vide', () => {
    expect(reasonError('DELIVERY_FAILED', '')).toBeNull();
  });

  it('accepte 200 points de code, refuse 201', () => {
    expect(reasonError('DELIVERY_FAILED', '😀'.repeat(REASON_MAX))).toBeNull();
    expect(reasonError('DELIVERY_FAILED', '😀'.repeat(REASON_MAX + 1))).toMatch(/200/);
  });

  it('refuse un motif fait de blancs, espace insécable compris', () => {
    expect(reasonError('DELIVERY_FAILED', '   ')).not.toBeNull();
    expect(reasonError('DELIVERY_FAILED', ' ')).not.toBeNull();
  });

  it('n’envoie un motif qu’avec DELIVERY_FAILED et s’il n’est pas vide', () => {
    expect(reasonToSend('DELIVERY_FAILED', 'Absent')).toBe('Absent');
    expect(reasonToSend('DELIVERY_FAILED', '')).toBeUndefined();
    expect(reasonToSend('DELIVERED', 'Absent')).toBeUndefined();
  });

  it('ignore le motif d’un autre type dans la validation', () => {
    expect(reasonError('DELIVERED', 'x'.repeat(500))).toBeNull();
  });
});
