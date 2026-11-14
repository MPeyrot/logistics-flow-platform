import type { AnomalyEntry, Scan } from './shipment.model';

export type TimelineEntry =
  | { kind: 'scan'; key: string; at: string; scan: Scan }
  | { kind: 'anomaly-opened'; key: string; at: string; anomaly: AnomalyEntry }
  | { kind: 'anomaly-closed'; key: string; at: string; anomaly: AnomalyEntry };

// Ordre de deux événements d'anomalie de même date.
const RANK = { 'anomaly-opened': 0, scan: 1, 'anomaly-closed': 2 } as const;

const byTime = (a: TimelineEntry, b: TimelineEntry) =>
  Date.parse(a.at) - Date.parse(b.at) || RANK[a.kind] - RANK[b.kind];

/**
 * Frise : les scans gardent leur ordre (date métier, puis réception). Chaque événement d'anomalie
 * vient après le dernier scan reçu avant lui : STALLED se compte depuis la réception, pas depuis
 * la date du client. À égalité de réception, l'ouverture précède le scan, la clôture le suit.
 */
export function buildTimeline(scans: Scan[], anomalies: AnomalyEntry[]): TimelineEntry[] {
  const ordered = [...scans].sort(
    (a, b) =>
      Date.parse(a.occurredAt) - Date.parse(b.occurredAt) ||
      Date.parse(a.receivedAt) - Date.parse(b.receivedAt),
  );
  const events: TimelineEntry[] = [
    ...anomalies.map(
      (anomaly): TimelineEntry => ({
        kind: 'anomaly-opened',
        key: anomaly.id,
        at: anomaly.detectedAt,
        anomaly,
      }),
    ),
    ...anomalies.flatMap((anomaly): TimelineEntry[] =>
      anomaly.resolvedAt === null
        ? []
        : [{ kind: 'anomaly-closed', key: anomaly.id, at: anomaly.resolvedAt, anomaly }],
    ),
  ];
  // slots[i + 1] : événements placés après le scan i ; slots[0] : avant tous les scans.
  const slots: TimelineEntry[][] = Array.from({ length: ordered.length + 1 }, () => []);
  for (const event of events) {
    const at = Date.parse(event.at);
    let index = -1;
    ordered.forEach((scan, i) => {
      const received = Date.parse(scan.receivedAt);
      if (event.kind === 'anomaly-closed' ? received <= at : received < at) {
        index = i;
      }
    });
    slots[index + 1].push(event);
  }
  return [
    ...slots[0].sort(byTime),
    ...ordered.flatMap((scan, i): TimelineEntry[] => [
      { kind: 'scan', key: scan.eventId, at: scan.occurredAt, scan },
      ...slots[i + 1].sort(byTime),
    ]),
  ];
}
