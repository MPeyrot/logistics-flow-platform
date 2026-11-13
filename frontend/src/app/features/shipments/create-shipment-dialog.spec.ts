import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { CreateShipmentDialog, toLocalInput, type ShipmentPrefill } from './create-shipment-dialog';

function setup(data: ShipmentPrefill | null) {
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  const fixture = TestBed.createComponent(CreateShipmentDialog);
  fixture.detectChanges();
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  return { fixture, close, navigate, http: TestBed.inject(HttpTestingController) };
}

const values = (fixture: ReturnType<typeof setup>['fixture']) =>
  (fixture.componentInstance as unknown as { form: { getRawValue(): object } }).form.getRawValue();

describe('CreateShipmentDialog', () => {
  it('s’ouvre vide sans valeurs initiales', () => {
    const { fixture } = setup(null);
    expect(values(fixture)).toEqual({ reference: '', origin: '', destination: '', expectedDeliveryAt: '' });
  });

  it('préremplit les champs fournis, la livraison prévue en heure locale', () => {
    const { fixture } = setup({
      reference: 'BL-42',
      expectedDeliveryAt: new Date(2026, 10, 20, 18, 0),
    });
    expect(values(fixture)).toEqual({
      reference: 'BL-42',
      origin: '',
      destination: '',
      expectedDeliveryAt: '2026-11-20T18:00',
    });
  });

  it('envoie la livraison prévue en UTC et ouvre le détail au succès', () => {
    const { fixture, http, close, navigate } = setup({
      reference: 'BL-42',
      origin: 'Paris',
      destination: 'Lyon',
      expectedDeliveryAt: new Date(2026, 10, 20, 18, 0),
    });
    (fixture.componentInstance as unknown as { submit(): void }).submit();
    const req = http.expectOne('/api/shipments');
    expect(req.request.body).toEqual({
      reference: 'BL-42',
      origin: 'Paris',
      destination: 'Lyon',
      expectedDeliveryAt: new Date(2026, 10, 20, 18, 0).toISOString(),
    });
    req.flush({ reference: 'BL-42' });
    expect(close).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/shipments', 'BL-42']);
  });

  it('affiche le detail du backend et reste ouvert en cas d’erreur', () => {
    const { fixture, http, close } = setup({
      reference: 'BL-42',
      origin: 'Paris',
      destination: 'Lyon',
      expectedDeliveryAt: new Date(2026, 10, 20, 18, 0),
    });
    (fixture.componentInstance as unknown as { submit(): void }).submit();
    http
      .expectOne('/api/shipments')
      .flush({ detail: 'Reference already exists' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Reference already exists');
    expect(close).not.toHaveBeenCalled();
  });
});

describe('toLocalInput', () => {
  it('formate en date et heure locales pour datetime-local', () => {
    expect(toLocalInput(new Date(2026, 0, 5, 9, 7))).toBe('2026-01-05T09:07');
  });
});
