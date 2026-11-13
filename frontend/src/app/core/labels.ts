// Libellés français des valeurs de l'API. Un `Record` sur l'union complète : une valeur
// ajoutée à l'API sans libellé ne compile pas.
export type ShipmentStatus =
  | 'CREATED'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'DELIVERY_FAILED';
export type ScanType =
  | 'PICKED_UP'
  | 'ARRIVED_AT_SITE'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'DELIVERY_FAILED';
export type AnomalyType = 'LATE' | 'STALLED';
export type Role = 'ADMIN' | 'OPERATOR' | 'SCANNER';

export const SHIPMENT_STATUS_LABELS: Record<ShipmentStatus, string> = {
  CREATED: 'Créée',
  IN_TRANSIT: 'En transit',
  OUT_FOR_DELIVERY: 'En cours de livraison',
  DELIVERED: 'Livrée',
  DELIVERY_FAILED: 'Livraison échouée',
};

export const SCAN_TYPE_LABELS: Record<ScanType, string> = {
  PICKED_UP: 'Prise en charge',
  ARRIVED_AT_SITE: 'Arrivée sur un site',
  OUT_FOR_DELIVERY: 'Départ en livraison',
  DELIVERED: 'Livré',
  DELIVERY_FAILED: 'Livraison échouée',
};

export const ANOMALY_TYPE_LABELS: Record<AnomalyType, string> = {
  LATE: 'En retard',
  STALLED: 'Sans mouvement',
};

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  OPERATOR: 'Opérateur',
  SCANNER: 'Scanneur',
};
