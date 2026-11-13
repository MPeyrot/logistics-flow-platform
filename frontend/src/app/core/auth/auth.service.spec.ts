import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService, EXPIRES_AT_KEY, TOKEN_KEY } from './auth.service';
import { fakeToken } from './fake-token';

const NOW = new Date('2026-11-13T10:00:00Z');
const inFuture = (ms: number) => new Date(NOW.getTime() + ms).toISOString();

function setup() {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  const router = TestBed.inject(Router);
  const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController), navigate };
}

function store(claims: Record<string, unknown>, expiresAt: string) {
  localStorage.setItem(TOKEN_KEY, fakeToken(claims));
  localStorage.setItem(EXPIRES_AT_KEY, expiresAt);
}

describe('AuthService', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    localStorage.clear();
  });
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('stocke le jeton et expiresAt à la connexion et lit les claims', () => {
    const { auth, http } = setup();
    const token = fakeToken({ sub: 'alice', roles: ['SCANNER'], site: 'PAR-01' });
    const expiresAt = inFuture(3_600_000);

    auth.login('alice', 'secret').subscribe();
    const req = http.expectOne('/api/auth/token');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'alice', password: 'secret' });
    req.flush({ accessToken: token, tokenType: 'Bearer', expiresAt });

    expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
    expect(localStorage.getItem(EXPIRES_AT_KEY)).toBe(expiresAt);
    expect(auth.session()).toEqual({
      token,
      expiresAt,
      username: 'alice',
      roles: ['SCANNER'],
      site: 'PAR-01',
    });
  });

  it('reprend une session valide au démarrage', () => {
    store({ sub: 'bob', roles: ['ADMIN'] }, inFuture(60_000));
    const { auth } = setup();
    expect(auth.session()?.username).toBe('bob');
  });

  it('efface un jeton expiré au démarrage', () => {
    store({ sub: 'bob', roles: ['ADMIN'] }, inFuture(-1000));
    const { auth } = setup();
    expect(auth.session()).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(EXPIRES_AT_KEY)).toBeNull();
  });

  it('efface un jeton illisible au démarrage', () => {
    localStorage.setItem(TOKEN_KEY, 'pas-un-jwt');
    localStorage.setItem(EXPIRES_AT_KEY, inFuture(60_000));
    const { auth } = setup();
    expect(auth.session()).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('expire à expiresAt : efface le jeton et renvoie à la connexion', () => {
    store({ sub: 'bob', roles: ['ADMIN'] }, inFuture(60_000));
    const { auth, navigate } = setup();

    vi.advanceTimersByTime(59_999);
    expect(auth.session()).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(auth.session()).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: 1 } });
  });

  it('la déconnexion efface localement et renvoie à la connexion', () => {
    store({ sub: 'bob', roles: ['ADMIN'] }, inFuture(60_000));
    const { auth, navigate } = setup();

    auth.logout();

    expect(auth.session()).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
    vi.advanceTimersByTime(120_000);
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('suit la déconnexion d’un autre onglet (événement storage)', () => {
    store({ sub: 'bob', roles: ['ADMIN'] }, inFuture(60_000));
    const { auth, navigate } = setup();

    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
    window.dispatchEvent(new StorageEvent('storage', { key: TOKEN_KEY, oldValue: 'x', newValue: null }));

    expect(auth.session()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('suit la connexion d’un autre onglet (événement storage)', () => {
    const { auth } = setup();
    expect(auth.session()).toBeNull();

    store({ sub: 'carol', roles: ['OPERATOR'] }, inFuture(60_000));
    window.dispatchEvent(new StorageEvent('storage', { key: EXPIRES_AT_KEY }));

    expect(auth.session()?.username).toBe('carol');
  });

  it('ignore les événements storage sans rapport', () => {
    store({ sub: 'bob', roles: ['ADMIN'] }, inFuture(60_000));
    const { auth, navigate } = setup();
    window.dispatchEvent(new StorageEvent('storage', { key: 'autre' }));
    expect(auth.session()).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('une 401 de l’API efface le jeton et renvoie à la connexion avec returnUrl', () => {
    store({ sub: 'bob', roles: ['ADMIN'] }, inFuture(60_000));
    const { auth, navigate } = setup();

    auth.handleUnauthorized('/anomalies');

    expect(auth.session()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/anomalies', expired: 1 },
    });
  });
});
