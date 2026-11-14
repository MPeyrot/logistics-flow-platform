import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { EXPIRES_AT_KEY, TOKEN_KEY } from './core/auth/auth.service';
import { fakeToken } from './core/auth/fake-token';
import { menuFor } from './features/header/header';

async function render(claims?: Record<string, unknown>, path = '/') {
  localStorage.clear();
  if (claims) {
    localStorage.setItem(TOKEN_KEY, fakeToken(claims));
    localStorage.setItem(EXPIRES_AT_KEY, new Date(Date.now() + 3_600_000).toISOString());
  }
  await TestBed.configureTestingModule({
    imports: [App],
    providers: [
      provideRouter([]),
      { provide: Location, useValue: { path: () => path } },
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(App);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('App', () => {
  afterEach(() => localStorage.clear());

  it('affiche le titre sans session, sans menu ni déconnexion', async () => {
    const el = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Logistics Flow');
    expect(el.querySelector('nav')).toBeNull();
    expect(el.textContent).not.toContain('Se déconnecter');
  });

  it('affiche identifiant, rôle, site et déconnexion pour un SCANNER', async () => {
    const el = await render({ sub: 'alice', roles: ['SCANNER'], site: 'PAR-01' });
    const text = el.textContent ?? '';
    expect(text).toContain('alice');
    expect(text).toContain('Scanneur');
    expect(text).toContain('PAR-01');
    expect(text).toContain('Se déconnecter');
  });
});

describe('App ouverte sur /projet', () => {
  afterEach(() => localStorage.clear());

  it("n'affiche pas l'en-tête de l'application dès le premier rendu", async () => {
    const el = await render({ sub: 'alice', roles: ['ADMIN'] }, '/projet/ecrans');
    expect(el.querySelector('app-header')).toBeNull();
    expect(el.querySelector('.shell')?.classList.contains('with-side')).toBe(false);
  });
});

describe('menuFor', () => {
  const labels = (roles: Parameters<typeof menuFor>[0]) => menuFor(roles).map((m) => m.label);

  it('OPERATOR : Expéditions, Anomalies, Bons de livraison', () => {
    expect(labels(['OPERATOR'])).toEqual([
      'Tableau de bord',
      'Expéditions',
      'Anomalies',
      'Bons de livraison',
    ]);
  });

  it('ADMIN : en plus Comptes et Messages en échec', () => {
    expect(labels(['ADMIN'])).toEqual([
      'Tableau de bord',
      'Expéditions',
      'Anomalies',
      'Bons de livraison',
      'Comptes',
      'Messages en échec',
    ]);
  });

  it('SCANNER : Scan, Import CSV', () => {
    expect(labels(['SCANNER'])).toEqual(['Scan', 'Import CSV']);
  });
});
