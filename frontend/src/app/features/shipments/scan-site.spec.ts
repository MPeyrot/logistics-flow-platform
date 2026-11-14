import { defaultSite } from './scan-site';

describe('defaultSite', () => {
  const shipment = { origin: 'Paris', destination: 'Lyon' };

  it('l’origine pour la prise en charge', () => {
    expect(defaultSite('PICKED_UP', shipment)).toBe('Paris');
  });

  it('la destination pour les autres types', () => {
    expect(defaultSite('ARRIVED_AT_SITE', shipment)).toBe('Lyon');
    expect(defaultSite('DELIVERED', shipment)).toBe('Lyon');
  });
});
