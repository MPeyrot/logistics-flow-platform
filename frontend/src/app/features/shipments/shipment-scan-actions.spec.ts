import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type { ShipmentStatus } from '../../core/labels';
import { ShipmentScanActions } from './shipment-scan-actions';

function setup(status: ShipmentStatus) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const fixture = TestBed.createComponent(ShipmentScanActions);
  fixture.componentRef.setInput('shipment', {
    reference: 'A-1',
    status,
    origin: 'Paris',
    destination: 'Lyon',
  });
  fixture.detectChanges();
  const el: HTMLElement = fixture.nativeElement;
  const buttons = () => Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());
  const site = () => (el.querySelector('input') as HTMLInputElement).value;
  return { fixture, el, buttons, site, http: TestBed.inject(HttpTestingController) };
}

describe('ShipmentScanActions', () => {
  it.each([
    ['CREATED', ['Prise en charge']],
    ['IN_TRANSIT', ['Arrivée au site', 'En livraison']],
    ['OUT_FOR_DELIVERY', ['Livrée', 'Échec de livraison']],
    ['DELIVERY_FAILED', ['Arrivée au site']],
    ['DELIVERED', []],
  ] as [ShipmentStatus, string[]][])('boutons proposés depuis %s', (status, expected) => {
    expect(setup(status).buttons()).toEqual(expected);
  });

  it('site prérempli : origine pour la prise en charge, destination sinon', () => {
    expect(setup('CREATED').site()).toBe('Paris');
    TestBed.resetTestingModule();
    expect(setup('IN_TRANSIT').site()).toBe('Lyon');
  });

  it('envoie le scan avec eventId, sans occurredAt, et le motif d’un échec', () => {
    const { fixture, el, http } = setup('OUT_FOR_DELIVERY');
    const textarea = el.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Absent';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const accepted: unknown[] = [];
    fixture.componentInstance.accepted.subscribe((a) => accepted.push(a));

    (
      Array.from(el.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Échec'),
      ) as HTMLElement
    ).click();
    const req = http.expectOne('/api/scans');
    expect(req.request.body).toMatchObject({
      shipmentReference: 'A-1',
      type: 'DELIVERY_FAILED',
      site: 'Lyon',
      reason: 'Absent',
    });
    expect(req.request.body.occurredAt).toBeUndefined();
    expect(req.request.body.eventId).toBeTruthy();
    req.flush({ eventId: req.request.body.eventId }, { status: 202, statusText: 'Accepted' });
    expect(accepted).toEqual([{ eventId: req.request.body.eventId, status: 'OUT_FOR_DELIVERY' }]);
  });
});
