import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HANDLES_ERRORS } from '../../../core/http/interceptors';
import type { DeadLetter, ReplayProgress } from './dlt.model';

const BASE = '/api/admin/dlt/scan-events';

@Injectable({ providedIn: 'root' })
export class DltService {
  private readonly http = inject(HttpClient);

  list(): Observable<DeadLetter[]> {
    return this.http.get<DeadLetter[]>(BASE);
  }

  /** Le 409 (« Déjà rejoué ») est affiché par l'écran. */
  replay(partition: number, offset: number): Observable<DeadLetter> {
    return this.http.post<DeadLetter>(`${BASE}/${partition}/${offset}/replay`, null, {
      context: new HttpContext().set(HANDLES_ERRORS, true),
    });
  }

  replayAll(): Observable<ReplayProgress> {
    return this.http.post<ReplayProgress>(`${BASE}/replay`, null);
  }

  /** Interrogé chaque seconde : les erreurs passagères et le 404 sont gérés par le suivi. */
  progress(id: string): Observable<ReplayProgress> {
    return this.http.get<ReplayProgress>(`${BASE}/replay/${encodeURIComponent(id)}`, {
      context: new HttpContext().set(HANDLES_ERRORS, true),
    });
  }
}
