import type { Shipment } from '../shipments/shipment.model';

export interface DeliveryNote {
  reference: string;
  deliveryDate: string | null;
  senderName: string | null;
  recipientName: string | null;
  recipientAddress: string | null;
  packageCount: number | null;
  totalWeightKg: number | null;
}

export interface DeliveryNoteReading {
  deliveryNote: DeliveryNote;
  costUsd: number;
  shipment: Shipment | null;
}
