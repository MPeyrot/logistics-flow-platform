import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Role } from '../labels';
import { EXPIRES_AT_KEY, TOKEN_KEY } from './auth.service';
import { fakeToken } from './fake-token';
import { authGuard, homeGuard, roleGuard } from './guards';

function run(guard: CanActivateFn, url: string, role?: Role): boolean | string {
  localStorage.clear();
  if (role) {
    localStorage.setItem(TOKEN_KEY, fakeToken({ sub: 'u', roles: [role], site: 'S1' }));
    localStorage.setItem(EXPIRES_AT_KEY, new Date(Date.now() + 3_600_000).toISOString());
  }
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const result = TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
  );
  return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : (result as boolean);
}


describe('authGuard', () => {
  afterEach(() => localStorage.clear());

  it('laisse passer un compte connecté', () => {
    expect(run(authGuard, '/anomalies', 'ADMIN')).toBe(true);
  });

  it('renvoie à la connexion avec returnUrl', () => {
    expect(run(authGuard, '/shipments/ABC', undefined)).toBe('/login?returnUrl=%2Fshipments%2FABC');
  });
});

describe('roleGuard', () => {
  afterEach(() => localStorage.clear());

  it('laisse passer un rôle permis', () => {
    expect(run(roleGuard('ADMIN', 'OPERATOR'), '/shipments', 'OPERATOR')).toBe(true);
  });

  it.each<[Role, Role[]]>([
    ['SCANNER', ['ADMIN', 'OPERATOR']],
    ['OPERATOR', ['ADMIN']],
    ['ADMIN', ['SCANNER']],
  ])('%s hors de %j : page Accès refusé', (role, allowed) => {
    expect(run(roleGuard(...allowed), '/x', role)).toBe('/access-denied');
  });

  it('sans session : connexion', () => {
    expect(run(roleGuard('ADMIN'), '/x')).toBe('/login');
  });
});

describe('homeGuard', () => {
  afterEach(() => localStorage.clear());

  it.each<[Role, string]>([
    ['ADMIN', '/dashboard'],
    ['OPERATOR', '/dashboard'],
    ['SCANNER', '/scan'],
  ])('%s arrive sur %s', (role, url) => {
    expect(run(homeGuard, '/', role)).toBe(url);
  });

  it('sans session : présentation du projet', () => {
    expect(run(homeGuard, '/')).toBe('/projet');
  });
});
