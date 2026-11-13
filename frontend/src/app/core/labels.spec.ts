import {
  ANOMALY_TYPE_LABELS,
  ROLE_LABELS,
  SCAN_TYPE_LABELS,
  SHIPMENT_STATUS_LABELS,
} from './labels';

// Valeurs de l'API (shipment-service et contracts/scan-event.v2.schema.json), écrites en dur.
describe('libellés', () => {
  it('donne un libellé français à chaque statut d’expédition', () => {
    expect(SHIPMENT_STATUS_LABELS).toEqual({
      CREATED: 'Créée',
      IN_TRANSIT: 'En transit',
      OUT_FOR_DELIVERY: 'En cours de livraison',
      DELIVERED: 'Livrée',
      DELIVERY_FAILED: 'Livraison échouée',
    });
  });

  it('donne un libellé français à chaque type de scan', () => {
    expect(SCAN_TYPE_LABELS).toEqual({
      PICKED_UP: 'Prise en charge',
      ARRIVED_AT_SITE: 'Arrivée sur un site',
      OUT_FOR_DELIVERY: 'Départ en livraison',
      DELIVERED: 'Livré',
      DELIVERY_FAILED: 'Livraison échouée',
    });
  });

  it('donne un libellé français à chaque type d’anomalie', () => {
    expect(ANOMALY_TYPE_LABELS).toEqual({
      LATE: 'En retard',
      STALLED: 'Sans mouvement',
    });
  });

  it('donne un libellé français à chaque rôle', () => {
    expect(ROLE_LABELS).toEqual({
      ADMIN: 'Administrateur',
      OPERATOR: 'Opérateur',
      SCANNER: 'Scanneur',
    });
  });
});
