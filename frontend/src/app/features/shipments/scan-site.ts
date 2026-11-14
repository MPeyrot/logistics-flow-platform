import type { ScanType } from '../../core/labels';

/** Site proposé pour un scan : l'origine pour PICKED_UP, la destination sinon. */
export function defaultSite(
  type: ScanType,
  shipment: { origin: string; destination: string },
): string {
  return type === 'PICKED_UP' ? shipment.origin : shipment.destination;
}
