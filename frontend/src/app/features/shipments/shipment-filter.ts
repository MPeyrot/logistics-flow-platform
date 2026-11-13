import { SHIPMENT_STATUS_LABELS, type ShipmentStatus } from '../../core/labels';
import type { Shipment } from './shipment.model';

export interface ShipmentFilter {
  search: string;
  status: ShipmentStatus | null;
  withAnomaly: boolean;
}

export type SortColumn =
  | 'reference'
  | 'origin'
  | 'destination'
  | 'status'
  | 'expectedDeliveryAt'
  | 'openAnomalies';

export interface SortState {
  column: SortColumn;
  /** Chaîne vide : ordre reçu du serveur. */
  direction: 'asc' | 'desc' | '';
}

export function filterShipments(shipments: Shipment[], filter: ShipmentFilter): Shipment[] {
  const search = filter.search.trim().toLowerCase();
  return shipments.filter(
    (s) =>
      (search === '' || s.reference.toLowerCase().includes(search)) &&
      (filter.status === null || s.status === filter.status) &&
      (!filter.withAnomaly || s.openAnomalies.length > 0),
  );
}

const text = (a: string, b: string) => a.localeCompare(b, 'fr', { sensitivity: 'base' });

/** Copie triée ; les livraisons prévues absentes restent en dernier dans les deux sens. */
export function sortShipments(shipments: Shipment[], sort: SortState): Shipment[] {
  if (sort.direction === '') {
    return [...shipments];
  }
  const sign = sort.direction === 'asc' ? 1 : -1;
  return [...shipments].sort((a, b) => {
    switch (sort.column) {
      case 'reference':
      case 'origin':
      case 'destination':
        return sign * text(a[sort.column], b[sort.column]);
      case 'status':
        return sign * text(SHIPMENT_STATUS_LABELS[a.status], SHIPMENT_STATUS_LABELS[b.status]);
      case 'openAnomalies':
        return sign * (a.openAnomalies.length - b.openAnomalies.length);
      case 'expectedDeliveryAt': {
        if (a.expectedDeliveryAt === null || b.expectedDeliveryAt === null) {
          return (a.expectedDeliveryAt === null ? 1 : 0) - (b.expectedDeliveryAt === null ? 1 : 0);
        }
        return sign * (Date.parse(a.expectedDeliveryAt) - Date.parse(b.expectedDeliveryAt));
      }
    }
  });
}
