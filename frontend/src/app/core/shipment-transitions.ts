import { ScanType, ShipmentStatus } from './labels';

export interface ShipmentTransition {
  from: ShipmentStatus;
  scan: ScanType;
  to: ShipmentStatus;
}

/** Les six transitions de `Shipment.applyScan` (shipment-service). Toute autre combinaison est refusée. */
export const SHIPMENT_TRANSITIONS: readonly ShipmentTransition[] = [
  { from: 'CREATED', scan: 'PICKED_UP', to: 'IN_TRANSIT' },
  { from: 'IN_TRANSIT', scan: 'ARRIVED_AT_SITE', to: 'IN_TRANSIT' },
  { from: 'IN_TRANSIT', scan: 'OUT_FOR_DELIVERY', to: 'OUT_FOR_DELIVERY' },
  { from: 'OUT_FOR_DELIVERY', scan: 'DELIVERED', to: 'DELIVERED' },
  { from: 'OUT_FOR_DELIVERY', scan: 'DELIVERY_FAILED', to: 'DELIVERY_FAILED' },
  { from: 'DELIVERY_FAILED', scan: 'ARRIVED_AT_SITE', to: 'IN_TRANSIT' },
];

/** Combinaison refusée donnée en exemple : le message part en DLT sans nouvelle tentative. */
export const REFUSED_EXAMPLE: { from: ShipmentStatus; scan: ScanType } = {
  from: 'IN_TRANSIT',
  scan: 'DELIVERED',
};

export const transitionsFrom = (status: ShipmentStatus): ShipmentTransition[] =>
  SHIPMENT_TRANSITIONS.filter((t) => t.from === status);

export const transitionsTo = (status: ShipmentStatus): ShipmentTransition[] =>
  SHIPMENT_TRANSITIONS.filter((t) => t.to === status);
