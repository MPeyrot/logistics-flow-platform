import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HANDLES_ERRORS } from '../../core/http/interceptors';
import type { DeliveryNoteReading } from './delivery-note.model';

@Injectable({ providedIn: 'root' })
export class DeliveryNoteService {
  private readonly http = inject(HttpClient);

  /** Les erreurs (415, 422, 429, 502…) sont affichées par l'écran. */
  extract(file: File): Observable<DeliveryNoteReading> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<DeliveryNoteReading>('/api/delivery-notes/extract', body, {
      context: new HttpContext().set(HANDLES_ERRORS, true),
    });
  }
}
