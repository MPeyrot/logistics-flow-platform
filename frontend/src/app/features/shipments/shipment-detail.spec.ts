import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ShipmentDetailPage, SCAN_POLL } from './shipment-detail';

const detail = (reference: string) => ({
  id: reference,
  reference,
  origin: 'Paris',
  destination: 'Lyon',
  status: 'CREATED',
  createdAt: '2026-11-13T10:00:00Z',
  expectedDeliveryAt: '2026-11-20T10:00:00Z',
  openAnomalies: [],
  scans: [],
  anomalies: [],
});

describe('ShipmentDetailPage', () => {
  it('recharge l’expédition quand la référence de la route change', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'shipments/:reference', component: ShipmentDetailPage }]),
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/shipments/A-1');
    http.expectOne('/api/shipments/A-1').flush(detail('A-1'));
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('A-1');

    await TestBed.inject(Router).navigateByUrl('/shipments/B-2');
    http.expectOne('/api/shipments/B-2').flush(detail('B-2'));
    harness.detectChanges();
    const text = harness.routeNativeElement?.textContent ?? '';
    expect(text).toContain('B-2');
    expect(text).not.toContain('A-1');
  });

  it('signale un statut inchangé à la fin du rechargement après un scan accepté', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SCAN_POLL, useValue: { intervalMs: 1, attempts: 2 } },
        provideRouter([{ path: 'shipments/:reference', component: ShipmentDetailPage }]),
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/shipments/A-1', ShipmentDetailPage);
    http.expectOne('/api/shipments/A-1').flush(detail('A-1'));
    harness.detectChanges();

    const page = harness.routeNativeElement?.querySelector('app-shipment-scan-actions');
    expect(page).not.toBeNull();
    const done = (
      component as unknown as {
        onAccepted(s: { eventId: string; status: string }): Promise<void>;
      }
    ).onAccepted({ eventId: 'e1', status: 'CREATED' });
    for (let i = 0; i < 2; i++) {
      await new Promise((r) => setTimeout(r, 5));
      http.expectOne('/api/shipments/A-1').flush(detail('A-1'));
    }
    await done;
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('le statut n’a pas encore changé');
  });
});
