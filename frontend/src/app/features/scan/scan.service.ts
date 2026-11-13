import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HANDLES_ERRORS } from '../../core/http/interceptors';
import type { ScanType } from '../../core/labels';
import type { ImportReport } from './import-report';

export interface ScanRequest {
  eventId: string;
  shipmentReference: string;
  type: ScanType;
  site: string;
  occurredAt?: string;
  reason?: string;
}

/** Les erreurs sont affichées par les écrans du scan (sous le formulaire, compte à rebours). */
@Injectable({ providedIn: 'root' })
export class ScanService {
  private readonly http = inject(HttpClient);

  send(scan: ScanRequest): Observable<{ eventId: string }> {
    return this.http.post<{ eventId: string }>('/api/scans', scan, { context: this.context() });
  }

  importCsv(file: File): Observable<ImportReport> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ImportReport>('/api/scans/imports', body, { context: this.context() });
  }

  private context(): HttpContext {
    return new HttpContext().set(HANDLES_ERRORS, true);
  }
}
