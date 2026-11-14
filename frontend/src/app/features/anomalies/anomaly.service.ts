import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import type { AnomalyType } from '../../core/labels';

export interface AnomalyRow {
  id: string;
  shipmentReference: string;
  type: AnomalyType;
  detectedAt: string;
  resolvedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class AnomalyService {
  private readonly http = inject(HttpClient);

  /** `GET /api/anomalies?open=` : les ouvertes (`true`) ou les closes (`false`). */
  list(open: boolean): Observable<AnomalyRow[]> {
    return this.http.get<AnomalyRow[]>('/api/anomalies', { params: { open } });
  }
}
