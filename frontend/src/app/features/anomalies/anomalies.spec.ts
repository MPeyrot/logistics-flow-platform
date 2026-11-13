import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Anomalies, type AnomalyRow } from './anomalies';

const row = (id: string, closed: boolean): AnomalyRow => ({
  id,
  shipmentReference: `REF-${id}`,
  type: 'LATE',
  detectedAt: '2026-11-13T10:00:00Z',
  resolvedAt: closed ? '2026-11-13T11:00:00Z' : null,
});

describe('Anomalies', () => {
  it('annule la requête de l’onglet quitté : sa réponse tardive ne s’affiche pas', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Anomalies);
    const component = fixture.componentInstance as unknown as { select(index: number): void };
    const open = http.expectOne((r) => r.params.get('open') === 'true');

    component.select(1);
    const closed = http.expectOne((r) => r.params.get('open') === 'false');
    expect(open.cancelled).toBe(true);

    closed.flush([row('c1', true)]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('REF-c1');
    expect(fixture.nativeElement.textContent).not.toContain('REF-o1');
  });
});
