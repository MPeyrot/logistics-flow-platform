import { filterShipments, sortShipments, type SortState } from './shipment-filter';
import type { Shipment } from './shipment.model';

function shipment(over: Partial<Shipment>): Shipment {
  return {
    id: over.reference ?? 'id',
    reference: 'REF-1',
    origin: 'Paris',
    destination: 'Lyon',
    status: 'CREATED',
    createdAt: '2026-11-01T10:00:00Z',
    expectedDeliveryAt: '2026-11-10T10:00:00Z',
    openAnomalies: [],
    ...over,
  };
}

const A = shipment({ reference: 'ABC-1', status: 'IN_TRANSIT', expectedDeliveryAt: '2026-11-12T10:00:00Z' });
const B = shipment({
  reference: 'xyz-2',
  status: 'DELIVERED',
  expectedDeliveryAt: null,
  openAnomalies: [{ type: 'LATE', detectedAt: '2026-11-13T10:00:00Z' }],
});
const C = shipment({
  reference: 'ABD-3',
  status: 'IN_TRANSIT',
  expectedDeliveryAt: '2026-11-11T10:00:00Z',
  openAnomalies: [
    { type: 'LATE', detectedAt: '2026-11-13T10:00:00Z' },
    { type: 'STALLED', detectedAt: '2026-11-13T11:00:00Z' },
  ],
});
const all = [A, B, C];

describe('filterShipments', () => {
  it('sans critère, garde tout', () => {
    expect(filterShipments(all, { search: '', status: null, withAnomaly: false })).toEqual(all);
  });

  it('cherche la référence sans tenir compte de la casse ni des espaces autour', () => {
    expect(filterShipments(all, { search: ' ab', status: null, withAnomaly: false })).toEqual([A, C]);
    expect(filterShipments(all, { search: 'XYZ', status: null, withAnomaly: false })).toEqual([B]);
  });

  it('filtre par statut', () => {
    expect(filterShipments(all, { search: '', status: 'IN_TRANSIT', withAnomaly: false })).toEqual([A, C]);
  });

  it('ne garde que les expéditions avec une anomalie ouverte', () => {
    expect(filterShipments(all, { search: '', status: null, withAnomaly: true })).toEqual([B, C]);
  });

  it('combine les critères', () => {
    expect(filterShipments(all, { search: 'ab', status: 'IN_TRANSIT', withAnomaly: true })).toEqual([C]);
  });
});

describe('sortShipments', () => {
  const refs = (list: Shipment[]) => list.map((s) => s.reference);
  const sort = (column: SortState['column'], direction: SortState['direction']) =>
    refs(sortShipments(all, { column, direction }));

  it('ne modifie pas la liste d’origine', () => {
    sortShipments(all, { column: 'reference', direction: 'desc' });
    expect(refs(all)).toEqual(['ABC-1', 'xyz-2', 'ABD-3']);
  });

  it('trie par référence, croissant puis décroissant', () => {
    expect(sort('reference', 'asc')).toEqual(['ABC-1', 'ABD-3', 'xyz-2']);
    expect(sort('reference', 'desc')).toEqual(['xyz-2', 'ABD-3', 'ABC-1']);
  });

  it('trie par livraison prévue, les dates absentes en dernier dans les deux sens', () => {
    expect(sort('expectedDeliveryAt', 'asc')).toEqual(['ABD-3', 'ABC-1', 'xyz-2']);
    expect(sort('expectedDeliveryAt', 'desc')).toEqual(['ABC-1', 'ABD-3', 'xyz-2']);
  });

  it('trie par statut selon son libellé français', () => {
    // « En transit » < « Livrée »
    expect(sort('status', 'asc')).toEqual(['ABC-1', 'ABD-3', 'xyz-2']);
  });

  it('trie par nombre d’anomalies ouvertes', () => {
    expect(sort('openAnomalies', 'desc')).toEqual(['ABD-3', 'xyz-2', 'ABC-1']);
  });

  it('sans direction, garde l’ordre reçu', () => {
    expect(refs(sortShipments(all, { column: 'reference', direction: '' }))).toEqual(refs(all));
  });
});
