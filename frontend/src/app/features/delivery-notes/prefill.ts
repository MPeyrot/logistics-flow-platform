import type { ShipmentPrefill } from '../shipments/create-shipment-dialog';
import type { DeliveryNote } from './delivery-note.model';

/** Valeurs du dialogue de création depuis un bon : livraison prévue = date du bon à 18 h locale. */
export function prefillFromNote(note: Pick<DeliveryNote, 'reference' | 'deliveryDate'>): ShipmentPrefill {
  const prefill: ShipmentPrefill = { reference: note.reference };
  const match = note.deliveryDate ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(note.deliveryDate) : null;
  if (match) {
    prefill.expectedDeliveryAt = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 18, 0);
  }
  return prefill;
}
