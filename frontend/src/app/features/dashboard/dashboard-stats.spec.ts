import type { AnomalyRow } from '../anomalies/anomaly.service';
import type { Shipment } from '../shipments/shipment.model';
import {
  countByStatus,
  countByType,
  dueWithin24h,
  latestAnomalies,
} from './dashboard-stats';

const NOW = new Date('2026-11-14T12:00:00Z');
const ship = (over: Partial<Shipment>): Shipment => ({
  id: over.reference ?? 'x',
  reference: 'REF',
  origin: 'Paris',
  destination: 'Lyon',
  status: 'IN_TRANSIT',
  createdAt: '2026-11-01T10:00:00Z',
  expectedDeliveryAt: null,
  openAnomalies: [],
  ...over,
});
const anomaly = (id: string, type: 'LATE' | 'STALLED', detectedAt: string): AnomalyRow => ({
  id,
  shipmentReference: `REF-${id}`,
  type,
  detectedAt,
  resolvedAt: null,
});

describe('dashboard-stats', () => {
  it('compte les expéditions par statut, zéro compris', () => {
    const counts = countByStatus([
      ship({ status: 'CREATED' }),
      ship({ status: 'DELIVERED' }),
      ship({ status: 'DELIVERED' }),
    ]);
    expect(counts).toEqual({
      CREATED: 1,
      IN_TRANSIT: 0,
      OUT_FOR_DELIVERY: 0,
      DELIVERED: 2,
      DELIVERY_FAILED: 0,
    });
  });

  it('compte les anomalies ouvertes par type', () => {
    const rows = [anomaly('1', 'LATE', 'a'), anomaly('2', 'STALLED', 'b'), anomaly('3', 'LATE', 'c')];
    expect(countByType(rows)).toEqual({ LATE: 2, STALLED: 1 });
  });

  it('garde les 5 anomalies les plus récentes', () => {
    const rows = [1, 2, 3, 4, 5, 6, 7].map((n) => anomaly(String(n), 'LATE', `2026-11-0${n}T10:00:00Z`));
    expect(latestAnomalies(rows).map((a) => a.id)).toEqual(['7', '6', '5', '4', '3']);
  });

  it('liste les non livrées attendues sous 24 h, les plus proches d’abord', () => {
    const list = [
      ship({ reference: 'A', expectedDeliveryAt: '2026-11-15T11:59:00Z' }),
      ship({ reference: 'B', expectedDeliveryAt: '2026-11-15T12:01:00Z' }),
      ship({ reference: 'C', expectedDeliveryAt: '2026-11-14T13:00:00Z' }),
      ship({ reference: 'D', expectedDeliveryAt: '2026-11-14T13:00:00Z', status: 'DELIVERED' }),
      ship({ reference: 'E', expectedDeliveryAt: null }),
      ship({ reference: 'F', expectedDeliveryAt: '2026-11-14T08:00:00Z' }),
    ];
    expect(dueWithin24h(list, NOW).map((s) => s.reference)).toEqual(['C', 'A']);
  });
});
