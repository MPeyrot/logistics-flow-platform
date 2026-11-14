import type { AnomalyEntry, Scan } from './shipment.model';
import { buildTimeline } from './timeline';

const scan = (eventId: string, occurredAt: string): Scan => ({
  eventId,
  type: 'ARRIVED_AT_SITE',
  site: 'Lyon',
  occurredAt,
  receivedAt: occurredAt,
});

const anomaly = (id: string, detectedAt: string, resolvedAt: string | null): AnomalyEntry => ({
  id,
  type: 'STALLED',
  detectedAt,
  resolvedAt,
});

describe('buildTimeline', () => {
  it('insère l’ouverture et la clôture des anomalies parmi les scans, par date', () => {
    const entries = buildTimeline(
      [scan('s1', '2026-11-10T08:00:00Z'), scan('s2', '2026-11-14T08:00:00Z')],
      [anomaly('a1', '2026-11-12T08:00:00Z', '2026-11-14T08:00:00Z')],
    );
    expect(entries.map((e) => `${e.kind}:${e.key}`)).toEqual([
      'scan:s1',
      'anomaly-opened:a1',
      'scan:s2',
      'anomaly-closed:a1',
    ]);
  });

  it('une anomalie ouverte n’a pas de clôture', () => {
    const entries = buildTimeline([], [anomaly('a1', '2026-11-12T08:00:00Z', null)]);
    expect(entries.map((e) => e.kind)).toEqual(['anomaly-opened']);
  });

  it('à date égale : ouverture, puis scan, puis clôture', () => {
    const at = '2026-11-12T08:00:00Z';
    const entries = buildTimeline([scan('s1', at)], [anomaly('a1', at, at)]);
    expect(entries.map((e) => e.kind)).toEqual(['anomaly-opened', 'scan', 'anomaly-closed']);
  });
});
