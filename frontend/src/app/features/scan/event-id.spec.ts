import { describe, expect, it } from 'vitest';
import { EventIdKeeper } from './event-id';

describe('eventId d’un scan', () => {
  const scan = { shipmentReference: 'EXP-1', type: 'PICKED_UP', site: 'LYO', occurredAt: null };

  it('garde le même identifiant pour le même scan renvoyé', () => {
    const keeper = new EventIdKeeper();
    expect(keeper.idFor(scan)).toBe(keeper.idFor({ ...scan }));
  });

  it('en prend un neuf dès que la saisie change', () => {
    const keeper = new EventIdKeeper();
    const first = keeper.idFor(scan);
    expect(keeper.idFor({ ...scan, shipmentReference: 'EXP-2' })).not.toBe(first);
  });

  it('en prend un neuf après un succès', () => {
    const keeper = new EventIdKeeper();
    const first = keeper.idFor(scan);
    keeper.accepted();
    expect(keeper.idFor(scan)).not.toBe(first);
  });
});
