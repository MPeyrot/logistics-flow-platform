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

export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
  cacheWriteTokens: number;
  cacheReadTokens: number;
}

export interface DeliveryNoteReading {
  deliveryNote: DeliveryNote;
  usage: TokenUsage;
  costUsd: number;
  shipment: Shipment | null;
}
