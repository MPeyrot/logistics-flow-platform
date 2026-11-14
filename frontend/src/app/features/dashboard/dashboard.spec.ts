import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import type { DeadLetter } from '../admin/dlt/dlt.model';
import type { Shipment } from '../shipments/shipment.model';
import { Dashboard } from './dashboard';

const letter = (offset: number, replayed: boolean): DeadLetter => ({
  partition: 0,
  offset,
  timestamp: '2026-11-14T10:00:00Z',
  key: null,
  value: null,
  exceptionClass: null,
  exceptionMessage: null,
  replayed,
  replayedAt: replayed ? '2026-11-14T11:00:00Z' : null,
});

const shipment = (reference: string, expectedDeliveryAt: string): Shipment => ({
  id: reference,
  reference,
  origin: 'Paris',
  destination: 'Lyon',
  status: 'IN_TRANSIT',
  createdAt: '2026-11-01T10:00:00Z',
  expectedDeliveryAt,
  openAnomalies: [],
});

function setup(admin: boolean) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: AuthService, useValue: { hasRole: () => admin } },
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(Dashboard);
  return { http, fixture };
}

describe('Dashboard', () => {
  afterEach(() => vi.useRealTimers());

  it('ne compte pas les messages déjà rejoués', async () => {
    const { http, fixture } = setup(true);
    http.expectOne((r) => r.url === '/api/shipments').flush([]);
    http.expectOne((r) => r.url === '/api/anomalies').flush([]);
    http
      .expectOne('/api/admin/dlt/scan-events')
      .flush([letter(1, false), letter(2, true), letter(3, false)]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[data-testid=dlt-count]').textContent).toBe('2');
  });

  it('affiche « 100+ » quand la page des échecs est pleine', async () => {
    const { http, fixture } = setup(true);
    http.expectOne((r) => r.url === '/api/shipments').flush([]);
    http.expectOne((r) => r.url === '/api/anomalies').flush([]);
    http
      .expectOne('/api/admin/dlt/scan-events')
      .flush(Array.from({ length: 100 }, (_, i) => letter(i, false)));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[data-testid=dlt-count]').textContent).toBe('100+');
  });

  it('met à jour « attendues sous 24 h » avec l’heure qui passe', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-14T10:00:00Z'));
    const { http, fixture } = setup(false);
    http
      .expectOne((r) => r.url === '/api/shipments')
      .flush([shipment('LATER-1', '2026-11-15T11:00:00Z')]);
    http.expectOne((r) => r.url === '/api/anomalies').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid=due]')?.textContent ?? '').not.toContain('LATER-1');

    vi.advanceTimersByTime(2 * 3_600_000);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid=due]').textContent).toContain('LATER-1');
  });
});
