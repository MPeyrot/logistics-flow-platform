import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { EXPIRES_AT_KEY, TOKEN_KEY } from '../../../core/auth/auth.service';
import { fakeToken } from '../../../core/auth/fake-token';
import type { UserAccount } from './user.model';
import { Users } from './users';

const account = (over: Partial<UserAccount>): UserAccount => ({
  id: over.username ?? 'x',
  username: 'x',
  role: 'OPERATOR',
  site: null,
  active: true,
  createdAt: '2026-11-01T10:00:00Z',
  ...over,
});

const USERS = [
  account({ username: 'admin', role: 'ADMIN' }),
  account({ username: 'lea', role: 'SCANNER', site: 'PAR-01' }),
  account({ username: 'old', active: false }),
];

function setup(confirmed = true) {
  localStorage.setItem(TOKEN_KEY, fakeToken({ sub: 'admin', roles: ['ADMIN'] }));
  localStorage.setItem(EXPIRES_AT_KEY, new Date(Date.now() + 3_600_000).toISOString());
  const open = vi.fn(() => ({ afterClosed: () => of(confirmed) }));
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MatDialog, useValue: { open } },
    ],
  });
  const fixture = TestBed.createComponent(Users);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  http.expectOne('/api/users').flush(USERS);
  fixture.detectChanges();
  return { fixture, http, open, el: fixture.nativeElement as HTMLElement };
}

describe('Users', () => {
  afterEach(() => localStorage.clear());

  it('liste les comptes avec rôle, site et état', () => {
    const { el } = setup();
    const rows = Array.from(el.querySelectorAll('tbody tr')).map((r) => r.textContent);
    expect(rows).toHaveLength(3);
    expect(rows[1]).toContain('lea');
    expect(rows[1]).toContain('Scanneur');
    expect(rows[1]).toContain('PAR-01');
  });

  it('masque l’interrupteur sur le compte connecté', () => {
    const { el } = setup();
    const rows = el.querySelectorAll('tbody tr');
    expect(rows[0].querySelector('mat-slide-toggle')).toBeNull();
    expect(rows[1].querySelector('mat-slide-toggle')).not.toBeNull();
    expect(rows[2].querySelector('mat-slide-toggle')).not.toBeNull();
  });

  it('demande confirmation avant de désactiver, puis met à jour la ligne', () => {
    const { fixture, http, open, el } = setup();
    (fixture.componentInstance as unknown as { toggle(u: UserAccount): void }).toggle(USERS[1]);
    expect(open).toHaveBeenCalledOnce();
    const req = http.expectOne('/api/users/lea');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ active: false });
    req.flush({ ...USERS[1], active: false });
    fixture.detectChanges();
    expect(el.querySelectorAll('tbody tr')[1].textContent).toContain('Inactif');
  });

  it('ne désactive pas si la confirmation est refusée', () => {
    const { fixture, http } = setup(false);
    (fixture.componentInstance as unknown as { toggle(u: UserAccount): void }).toggle(USERS[1]);
    http.expectNone('/api/users/lea');
  });

  it('réactive sans confirmation', () => {
    const { fixture, http, open } = setup();
    (fixture.componentInstance as unknown as { toggle(u: UserAccount): void }).toggle(USERS[2]);
    expect(open).not.toHaveBeenCalled();
    expect(http.expectOne('/api/users/old').request.body).toEqual({ active: true });
  });

  function clickToggle(fixture: ComponentFixture<Users>, row: number): HTMLButtonElement {
    const el = fixture.nativeElement as HTMLElement;
    const button = el.querySelectorAll('tbody tr')[row].querySelector<HTMLButtonElement>('button[role=switch]')!;
    button.click();
    fixture.detectChanges();
    return button;
  }

  it('remet l’interrupteur sur « actif » quand la confirmation est refusée', () => {
    const { fixture } = setup(false);
    const button = clickToggle(fixture, 1);
    fixture.detectChanges();
    expect(button.getAttribute('aria-checked')).toBe('true');
  });

  it('remet l’interrupteur sur « actif » quand le PATCH échoue', () => {
    const { fixture, http } = setup();
    const button = clickToggle(fixture, 1);
    http.expectOne('/api/users/lea').flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(button.getAttribute('aria-checked')).toBe('true');
  });
});
