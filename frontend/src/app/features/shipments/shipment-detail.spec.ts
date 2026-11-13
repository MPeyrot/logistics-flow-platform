import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ShipmentDetailPage } from './shipment-detail';

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
});
