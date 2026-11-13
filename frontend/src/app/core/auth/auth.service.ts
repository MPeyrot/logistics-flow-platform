import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { Role } from '../labels';
import { decodeToken } from './jwt';

export const TOKEN_KEY = 'auth.token';
export const EXPIRES_AT_KEY = 'auth.expiresAt';
export const LOGIN_URL = '/api/auth/token';

// setTimeout plafonne à 2^31 - 1 ms (~24,8 jours) ; les jetons durent au plus 12 h.
const MAX_TIMEOUT_MS = 2_147_483_647;

export interface Session {
  token: string;
  expiresAt: string;
  username: string;
  roles: Role[];
  site: string | null;
}

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

/** Session du navigateur : jeton dans `localStorage`, expiration, synchronisation entre onglets. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly state = signal<Session | null>(null);
  private timer: ReturnType<typeof setTimeout> | undefined;

  /** Session courante, ou null si personne n'est connecté. */
  readonly session = this.state.asReadonly();

  constructor() {
    this.restore(true);
    window.addEventListener('storage', (event) => this.onStorage(event));
  }

  login(username: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(LOGIN_URL, { username, password })
      .pipe(
        tap((res) => {
          localStorage.setItem(TOKEN_KEY, res.accessToken);
          localStorage.setItem(EXPIRES_AT_KEY, res.expiresAt);
          this.restore();
        }),
      );
  }

  logout(): void {
    this.clear();
    void this.router.navigate(['/login']);
  }

  /** Une 401 de l'API : le serveur ne reconnaît plus le jeton. */
  handleUnauthorized(returnUrl: string): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { returnUrl, expired: 1 } });
  }

  hasRole(...roles: Role[]): boolean {
    const session = this.state();
    return session !== null && session.roles.some((r) => roles.includes(r));
  }

  /** Lit le stockage. `clearInvalid` : au démarrage, efface un jeton expiré ou illisible. */
  private restore(clearInvalid = false): void {
    clearTimeout(this.timer);
    const token = localStorage.getItem(TOKEN_KEY);
    const expiresAt = localStorage.getItem(EXPIRES_AT_KEY);
    const claims = token ? decodeToken(token) : null;
    const expiry = expiresAt ? Date.parse(expiresAt) : NaN;
    if (!token || !expiresAt || !claims || Number.isNaN(expiry) || expiry <= Date.now()) {
      // Sur un événement `storage`, ne rien effacer : l'autre onglet est peut-être en train
      // d'écrire le jeton puis expiresAt.
      if (clearInvalid) {
        this.clear();
      } else {
        this.state.set(null);
      }
      return;
    }
    this.state.set({ token, expiresAt, ...claims });
    this.timer = setTimeout(
      () => this.expire(),
      Math.min(expiry - Date.now(), MAX_TIMEOUT_MS),
    );
  }

  private expire(): void {
    // Le minuteur plafonné peut se déclencher avant l'échéance : on se réarme alors.
    const expiresAt = localStorage.getItem(EXPIRES_AT_KEY);
    if (expiresAt && Date.parse(expiresAt) > Date.now()) {
      this.restore();
      return;
    }
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  private onStorage(event: StorageEvent): void {
    if (event.key !== null && event.key !== TOKEN_KEY && event.key !== EXPIRES_AT_KEY) {
      return;
    }
    const wasLoggedIn = this.state() !== null;
    this.restore();
    if (wasLoggedIn && this.state() === null) {
      void this.router.navigate(['/login']);
    }
  }

  private clear(): void {
    clearTimeout(this.timer);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
    this.state.set(null);
  }
}
