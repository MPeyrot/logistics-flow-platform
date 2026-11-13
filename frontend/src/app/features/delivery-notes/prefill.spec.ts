import { prefillFromNote } from './prefill';

describe('prefillFromNote', () => {
  it('reprend la référence et fixe la livraison prévue à 18 h, heure locale', () => {
    const prefill = prefillFromNote({ reference: 'REF-1', deliveryDate: '2026-12-05' });
    expect(prefill.reference).toBe('REF-1');
    expect(prefill.expectedDeliveryAt).toEqual(new Date(2026, 11, 5, 18, 0));
    expect(prefill.origin).toBeUndefined();
    expect(prefill.destination).toBeUndefined();
  });

  it('laisse la livraison prévue vide sans date lue', () => {
    expect(prefillFromNote({ reference: 'REF-1', deliveryDate: null }).expectedDeliveryAt).toBeUndefined();
  });
});
