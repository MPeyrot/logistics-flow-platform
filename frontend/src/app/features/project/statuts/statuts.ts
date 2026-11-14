import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { SCAN_TYPE_LABELS, SHIPMENT_STATUS_LABELS, ScanType, ShipmentStatus } from '../../../core/labels';
import {
  REFUSED_EXAMPLE,
  SHIPMENT_TRANSITIONS,
  ShipmentTransition,
  transitionsFrom,
  transitionsTo,
} from '../../../core/shipment-transitions';

const NODE_WIDTH = 140;
const NODE_HEIGHT = 44;

/** Centre de chaque statut dans le `viewBox` 360 x 460 (colonne étroite, lisible à 360 px). */
const NODE_CENTERS: Record<ShipmentStatus, { x: number; y: number }> = {
  CREATED: { x: 180, y: 42 },
  IN_TRANSIT: { x: 180, y: 152 },
  OUT_FOR_DELIVERY: { x: 180, y: 262 },
  DELIVERED: { x: 90, y: 402 },
  DELIVERY_FAILED: { x: 270, y: 402 },
};

interface EdgeShape {
  path: string;
  labelX: number;
  labelY: number;
  anchor: 'start' | 'end';
}

/** Tracé de chaque flèche, indexé par `de>type de scan` (une par transition de la table). */
const EDGE_SHAPES: Record<string, EdgeShape> = {
  'CREATED>PICKED_UP': { path: 'M180 64 V128', labelX: 190, labelY: 100, anchor: 'start' },
  'IN_TRANSIT>ARRIVED_AT_SITE': {
    path: 'M110 142 C 40 122, 40 182, 108 162',
    labelX: 4,
    labelY: 114,
    anchor: 'start',
  },
  'IN_TRANSIT>OUT_FOR_DELIVERY': { path: 'M180 174 V238', labelX: 190, labelY: 210, anchor: 'start' },
  'OUT_FOR_DELIVERY>DELIVERED': { path: 'M150 284 L102 378', labelX: 118, labelY: 336, anchor: 'end' },
  'OUT_FOR_DELIVERY>DELIVERY_FAILED': { path: 'M210 284 L258 378', labelX: 254, labelY: 336, anchor: 'start' },
  'DELIVERY_FAILED>ARRIVED_AT_SITE': {
    path: 'M340 402 C 358 402, 358 152, 252 152',
    labelX: 310,
    labelY: 232,
    anchor: 'end',
  },
};

export interface DiagramNode {
  status: ShipmentStatus;
  label: string;
  x: number;
  y: number;
}

export interface DiagramEdge extends EdgeShape {
  key: string;
  from: ShipmentStatus;
  label: string;
}

/** Page `/projet/statuts` : diagramme SVG des statuts, lu depuis la table des transitions. */
@Component({
  selector: 'app-project-statuts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './statuts.scss',
  templateUrl: './statuts.html',
})
export class Statuts {
  protected readonly nodeWidth = NODE_WIDTH;
  protected readonly nodeHeight = NODE_HEIGHT;
  protected readonly selected = signal<ShipmentStatus | null>(null);

  protected readonly nodes: DiagramNode[] = (Object.keys(NODE_CENTERS) as ShipmentStatus[]).map((status) => ({
    status,
    label: SHIPMENT_STATUS_LABELS[status],
    x: NODE_CENTERS[status].x - NODE_WIDTH / 2,
    y: NODE_CENTERS[status].y - NODE_HEIGHT / 2,
  }));

  protected readonly edges: DiagramEdge[] = SHIPMENT_TRANSITIONS.map((t) => {
    const key = `${t.from}>${t.scan}`;
    return { ...EDGE_SHAPES[key], key, from: t.from, label: SCAN_TYPE_LABELS[t.scan] };
  });

  protected readonly transitions = SHIPMENT_TRANSITIONS;

  protected readonly details = computed(() => {
    const status = this.selected();
    if (!status) {
      return null;
    }
    return {
      status,
      label: SHIPMENT_STATUS_LABELS[status],
      outgoing: transitionsFrom(status),
      incoming: transitionsTo(status),
      refused: this.refusedExample(status),
    };
  });

  protected select(status: ShipmentStatus): void {
    this.selected.set(status);
  }

  protected statusLabel(status: ShipmentStatus): string {
    return SHIPMENT_STATUS_LABELS[status];
  }

  protected scanLabel(scan: ScanType): string {
    return SCAN_TYPE_LABELS[scan];
  }

  protected describe(t: ShipmentTransition): string {
    return `${SCAN_TYPE_LABELS[t.scan]} : ${SHIPMENT_STATUS_LABELS[t.from]} vers ${SHIPMENT_STATUS_LABELS[t.to]}`;
  }

  /** Un scan que ce statut refuse : l'exemple de la spec pour IN_TRANSIT, sinon le premier type non permis. */
  private refusedExample(status: ShipmentStatus): ScanType {
    if (status === REFUSED_EXAMPLE.from) {
      return REFUSED_EXAMPLE.scan;
    }
    const allowed = transitionsFrom(status).map((t) => t.scan);
    const all = Object.keys(SCAN_TYPE_LABELS) as ScanType[];
    return all.find((scan) => !allowed.includes(scan)) ?? REFUSED_EXAMPLE.scan;
  }
}
