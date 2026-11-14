import { describe, expect, it } from 'vitest';
import { REFUSED_EXAMPLE, SHIPMENT_TRANSITIONS, transitionsFrom, transitionsTo } from './shipment-transitions';

describe('SHIPMENT_TRANSITIONS', () => {
  it('compte les six transitions permises, dans l’ordre du cycle de vie', () => {
    expect(SHIPMENT_TRANSITIONS.map((t) => `${t.from} + ${t.scan} -> ${t.to}`)).toEqual([
      'CREATED + PICKED_UP -> IN_TRANSIT',
      'IN_TRANSIT + ARRIVED_AT_SITE -> IN_TRANSIT',
      'IN_TRANSIT + OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY',
      'OUT_FOR_DELIVERY + DELIVERED -> DELIVERED',
      'OUT_FOR_DELIVERY + DELIVERY_FAILED -> DELIVERY_FAILED',
      'DELIVERY_FAILED + ARRIVED_AT_SITE -> IN_TRANSIT',
    ]);
  });

  it('DELIVERED est terminal', () => {
    expect(transitionsFrom('DELIVERED')).toEqual([]);
  });

  it('donne les transitions sortantes et entrantes d’un statut', () => {
    expect(transitionsFrom('IN_TRANSIT').map((t) => t.scan)).toEqual(['ARRIVED_AT_SITE', 'OUT_FOR_DELIVERY']);
    expect(transitionsTo('IN_TRANSIT').map((t) => t.from)).toEqual(['CREATED', 'IN_TRANSIT', 'DELIVERY_FAILED']);
  });

  it('l’exemple refusé n’est pas dans la table', () => {
    expect(REFUSED_EXAMPLE).toEqual({ from: 'IN_TRANSIT', scan: 'DELIVERED' });
    expect(SHIPMENT_TRANSITIONS.some((t) => t.from === 'IN_TRANSIT' && t.scan === 'DELIVERED')).toBe(false);
  });
});
