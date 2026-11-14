import type { AnomalyType, ShipmentStatus } from '../../core/labels';
import type { Shipment } from '../shipments/shipment.model';

export interface OpenAnomalyRow {
  id: string;
  shipmentReference: string;
  type: AnomalyType;
  detectedAt: string;
  resolvedAt: string | null;
}

export const STATUSES: ShipmentStatus[] = [
  'CREATED',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'DELIVERY_FAILED',
];
const DAY_MS = 24 * 3_600_000;

export function countByStatus(shipments: Shipment[]): Record<ShipmentStatus, number> {
  const counts = Object.fromEntries(STATUSES.map((s) => [s, 0])) as Record<ShipmentStatus, number>;
  for (const s of shipments) {
    counts[s.status] += 1;
  }
  return counts;
}

export function countByType(anomalies: OpenAnomalyRow[]): Record<AnomalyType, number> {
  const counts: Record<AnomalyType, number> = { LATE: 0, STALLED: 0 };
  for (const a of anomalies) {
    counts[a.type] += 1;
  }
  return counts;
}

/** Les plus récentes d'abord (`detectedAt`). */
export function latestAnomalies(anomalies: OpenAnomalyRow[], limit = 5): OpenAnomalyRow[] {
  return [...anomalies]
    .sort((a, b) => Date.parse(b.detectedAt) - Date.parse(a.detectedAt))
    .slice(0, limit);
}

/** Non livrées, attendues d'ici 24 h (échéance dépassée exclue : c'est une anomalie LATE). */
export function dueWithin24h(shipments: Shipment[], now: Date): Shipment[] {
  const t = now.getTime();
  return shipments
    .filter((s) => {
      if (s.status === 'DELIVERED' || !s.expectedDeliveryAt) {
        return false;
      }
      const due = Date.parse(s.expectedDeliveryAt);
      return due >= t && due <= t + DAY_MS;
    })
    .sort((a, b) => Date.parse(a.expectedDeliveryAt!) - Date.parse(b.expectedDeliveryAt!));
}
