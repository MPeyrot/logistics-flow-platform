import type { AnomalyType, ShipmentStatus } from '../../core/labels';
import type { DeadLetter } from '../admin/dlt/dlt.model';
import type { AnomalyRow } from '../anomalies/anomaly.service';
import type { Shipment } from '../shipments/shipment.model';

/** Taille de la page de `GET /api/admin/dlt/scan-events` : au-delà, le compte n'est qu'un minimum. */
export const DLT_PAGE_SIZE = 100;

/** Messages en échec pas encore rejoués ; « N+ » si la page est pleine (il peut y en avoir d'autres). */
export function failedMessagesLabel(letters: DeadLetter[]): string {
  const pending = letters.filter((l) => !l.replayed).length;
  return letters.length >= DLT_PAGE_SIZE ? `${pending}+` : String(pending);
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

export function countByType(anomalies: AnomalyRow[]): Record<AnomalyType, number> {
  const counts: Record<AnomalyType, number> = { LATE: 0, STALLED: 0 };
  for (const a of anomalies) {
    counts[a.type] += 1;
  }
  return counts;
}

/** Les plus récentes d'abord (`detectedAt`). */
export function latestAnomalies(anomalies: AnomalyRow[], limit = 5): AnomalyRow[] {
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
