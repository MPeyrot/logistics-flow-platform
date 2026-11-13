import type { AnomalyType, ScanType, ShipmentStatus } from '../../core/labels';

export interface OpenAnomaly {
  type: AnomalyType;
  detectedAt: string;
}

export interface Shipment {
  id: string;
  reference: string;
  origin: string;
  destination: string;
  status: ShipmentStatus;
  createdAt: string;
  expectedDeliveryAt: string | null;
  openAnomalies: OpenAnomaly[];
}

export interface Scan {
  eventId: string;
  type: ScanType;
  site: string;
  occurredAt: string;
  receivedAt: string;
  reason?: string;
}

export interface AnomalyEntry {
  id: string;
  type: AnomalyType;
  detectedAt: string;
  resolvedAt: string | null;
}

export interface ShipmentDetail extends Shipment {
  scans: Scan[];
  anomalies: AnomalyEntry[];
}

export interface CreateShipment {
  reference: string;
  origin: string;
  destination: string;
  expectedDeliveryAt: string;
}
