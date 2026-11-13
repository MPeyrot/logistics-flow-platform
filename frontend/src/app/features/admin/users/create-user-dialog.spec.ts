import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { CreateUserDialog } from './create-user-dialog';

interface Internals {
  form: {
    patchValue(v: object): void;
    valid: boolean;
    controls: Record<string, { disabled: boolean }>;
  };
  submit(): void;
}

function setup() {
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  const fixture = TestBed.createComponent(CreateUserDialog);
  fixture.detectChanges();
  const dialog = fixture.componentInstance as unknown as Internals;
  return { fixture, dialog, close, http: TestBed.inject(HttpTestingController) };
}

const PASSWORD = 'un-mot-de-passe-long';
const base = { username: 'lea', password: PASSWORD, confirmation: PASSWORD };

describe('CreateUserDialog', () => {
  it('exige le site pour un SCANNER seulement', () => {
    const { dialog, fixture } = setup();
    dialog.form.patchValue({ ...base, role: 'OPERATOR', site: '' });
    expect(dialog.form.valid).toBe(true);
    dialog.form.patchValue({ role: 'SCANNER' });
    expect(dialog.form.valid).toBe(false);
    dialog.form.patchValue({ site: 'PAR-01' });
    expect(dialog.form.valid).toBe(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Site');
  });

  it('masque le site hors SCANNER', () => {
    const { dialog, fixture } = setup();
    dialog.form.patchValue({ role: 'ADMIN' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[formControlName="site"]')).toBeNull();
    dialog.form.patchValue({ role: 'SCANNER' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[formControlName="site"]')).not.toBeNull();
  });

  it('refuse un mot de passe trop court ou une confirmation différente', () => {
    const { dialog } = setup();
    dialog.form.patchValue({ ...base, role: 'ADMIN', password: 'court', confirmation: 'court' });
    expect(dialog.form.valid).toBe(false);
    dialog.form.patchValue({ password: PASSWORD, confirmation: PASSWORD + 'x' });
    expect(dialog.form.valid).toBe(false);
    dialog.form.patchValue({ confirmation: PASSWORD });
    expect(dialog.form.valid).toBe(true);
  });

  it('n’envoie pas de site pour un rôle autre que SCANNER', () => {
    const { dialog, http, close } = setup();
    dialog.form.patchValue({ ...base, role: 'OPERATOR', site: 'PAR-01' });
    dialog.submit();
    const req = http.expectOne('/api/users');
    expect(req.request.body).toEqual({
      username: 'lea',
      password: PASSWORD,
      role: 'OPERATOR',
      site: null,
    });
    req.flush({ id: '1' });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('affiche le detail du backend', () => {
    const { dialog, http, fixture } = setup();
    dialog.form.patchValue({ ...base, role: 'SCANNER', site: ' PAR-01 ' });
    dialog.submit();
    const req = http.expectOne('/api/users');
    expect((req.request.body as { site: string }).site).toBe('PAR-01');
    req.flush({ detail: 'Identifiant déjà pris' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Identifiant déjà pris',
    );
  });
});
