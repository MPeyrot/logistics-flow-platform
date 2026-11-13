import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { EXPIRES_AT_KEY, TOKEN_KEY } from '../auth/auth.service';
import { fakeToken } from '../auth/fake-token';
import { MessageService } from '../messages/message.service';
import { HANDLES_ERRORS, authInterceptor, errorInterceptor } from './interceptors';

const token = fakeToken({ sub: 'bob', roles: ['ADMIN'] });

function setup(loggedIn: boolean) {
  localStorage.clear();
  if (loggedIn) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(EXPIRES_AT_KEY, new Date(Date.now() + 3_600_000).toISOString());
  }
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  const router = TestBed.inject(Router);
  vi.spyOn(router, 'url', 'get').mockReturnValue('/anomalies');
  const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  return {
    http: TestBed.inject(HttpClient),
    ctrl: TestBed.inject(HttpTestingController),
    messages: TestBed.inject(MessageService),
    navigate,
  };
}

describe('authInterceptor', () => {
  afterEach(() => localStorage.clear());

  it('ajoute le Bearer sur /api/', () => {
    const { http, ctrl } = setup(true);
    http.get('/api/shipments').subscribe();
    expect(ctrl.expectOne('/api/shipments').request.headers.get('Authorization')).toBe(
      `Bearer ${token}`,
    );
  });

  it('n’ajoute rien sur la connexion', () => {
    const { http, ctrl } = setup(true);
    http.post('/api/auth/token', {}).subscribe();
    expect(ctrl.expectOne('/api/auth/token').request.headers.has('Authorization')).toBe(false);
  });

  it('n’ajoute rien hors de /api/', () => {
    const { http, ctrl } = setup(true);
    http.get('/assets/x.json').subscribe();
    expect(ctrl.expectOne('/assets/x.json').request.headers.has('Authorization')).toBe(false);
  });

  it('n’ajoute rien sans session', () => {
    const { http, ctrl } = setup(false);
    http.get('/api/shipments').subscribe();
    expect(ctrl.expectOne('/api/shipments').request.headers.has('Authorization')).toBe(false);
  });
});

describe('errorInterceptor', () => {
  afterEach(() => localStorage.clear());

  it('401 : efface le jeton et renvoie à la connexion avec returnUrl', () => {
    const { http, ctrl, navigate } = setup(true);
    http.get('/api/anomalies').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/anomalies').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/anomalies', expired: 1 },
    });
  });

  it('401 de la connexion : laissé à l’écran, jeton intact', () => {
    const { http, ctrl, navigate, messages } = setup(true);
    http.post('/api/auth/token', {}).subscribe({ error: () => undefined });
    ctrl.expectOne('/api/auth/token').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
    expect(messages.current()).toBeNull();
  });

  it('403 : « Accès refusé »', () => {
    const { http, ctrl, messages } = setup(true);
    http.get('/api/users').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/users').flush(null, { status: 403, statusText: 'Forbidden' });
    expect(messages.current()).toBe('Accès refusé');
  });

  it('409 problem+json : affiche detail', () => {
    const { http, ctrl, messages } = setup(true);
    http.post('/api/shipments', {}).subscribe({ error: () => undefined });
    ctrl
      .expectOne('/api/shipments')
      .flush({ detail: 'Référence déjà utilisée' }, { status: 409, statusText: 'Conflict' });
    expect(messages.current()).toBe('Référence déjà utilisée');
  });

  it('erreur réseau : message d’injoignabilité', () => {
    const { http, ctrl, messages } = setup(true);
    http.get('/api/shipments').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/shipments').error(new ProgressEvent('error'));
    expect(messages.current()).toContain('injoignable');
  });

  it('relance l’erreur à l’appelant', () => {
    const { http, ctrl } = setup(true);
    const onError = vi.fn();
    http.get('/api/shipments').subscribe({ error: onError });
    ctrl.expectOne('/api/shipments').flush(null, { status: 503, statusText: 'x' });
    expect(onError).toHaveBeenCalledOnce();
  });

  it('n’ajoute pas de message quand l’écran gère l’erreur', () => {
    const { http, ctrl, messages } = setup(true);
    http
      .get('/api/shipments', { context: new HttpContext().set(HANDLES_ERRORS, true) })
      .subscribe({ error: () => undefined });
    ctrl.expectOne('/api/shipments').flush(null, { status: 429, statusText: 'x' });
    expect(messages.current()).toBeNull();
  });
});
