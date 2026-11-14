import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ShipmentList } from './shipment-list';

async function setup(url: string) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([{ path: 'shipments', component: ShipmentList }]),
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  http.expectOne('/api/shipments').flush([]);
  harness.detectChanges();
  const select = () => harness.routeNativeElement?.querySelector('select') as HTMLSelectElement;
  return { harness, select, router: TestBed.inject(Router) };
}

describe('ShipmentList, filtre de statut dans l’URL', () => {
  it('suit le paramètre status quand l’URL change', async () => {
    const { harness, select, router } = await setup('/shipments?status=DELIVERED');
    await harness.fixture.whenStable();
    expect(select().value).toBe('DELIVERED');

    await router.navigateByUrl('/shipments');
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(select().value).toBe('');
  });

  it('écrit le filtre choisi dans l’URL', async () => {
    const { harness, select, router } = await setup('/shipments');
    select().value = 'IN_TRANSIT';
    select().dispatchEvent(new Event('change'));
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(router.url).toBe('/shipments?status=IN_TRANSIT');

    select().value = '';
    select().dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
    expect(router.url).toBe('/shipments');
  });
});
