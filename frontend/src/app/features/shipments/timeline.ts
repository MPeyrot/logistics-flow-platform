import type { AnomalyEntry, Scan } from './shipment.model';

export type TimelineEntry =
  | { kind: 'scan'; key: string; at: string; scan: Scan }
  | { kind: 'anomaly-opened'; key: string; at: string; anomaly: AnomalyEntry }
  | { kind: 'anomaly-closed'; key: string; at: string; anomaly: AnomalyEntry };

// À date égale : l'ouverture précède le scan, la clôture le suit.
const RANK = { 'anomaly-opened': 0, scan: 1, 'anomaly-closed': 2 } as const;

/** Frise : scans (date métier), ouvertures et clôtures d'anomalies, par date croissante. */
export function buildTimeline(scans: Scan[], anomalies: AnomalyEntry[]): TimelineEntry[] {
  const entries: TimelineEntry[] = [
    ...scans.map((scan): TimelineEntry => ({
      kind: 'scan',
      key: scan.eventId,
      at: scan.occurredAt,
      scan,
    })),
    ...anomalies.map((anomaly): TimelineEntry => ({
      kind: 'anomaly-opened',
      key: anomaly.id,
      at: anomaly.detectedAt,
      anomaly,
    })),
    ...anomalies.flatMap((anomaly): TimelineEntry[] =>
      anomaly.resolvedAt === null
        ? []
        : [{ kind: 'anomaly-closed', key: anomaly.id, at: anomaly.resolvedAt, anomaly }],
    ),
  ];
  return entries.sort((a, b) => Date.parse(a.at) - Date.parse(b.at) || RANK[a.kind] - RANK[b.kind]);
}
