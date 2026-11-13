import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HANDLES_ERRORS } from '../../core/http/interceptors';
import type { CreateShipment, Shipment, ShipmentDetail } from './shipment.model';

@Injectable({ providedIn: 'root' })
export class ShipmentService {
  private readonly http = inject(HttpClient);

  list(): Observable<Shipment[]> {
    return this.http.get<Shipment[]>('/api/shipments');
  }

  /** Le 404 est affiché par l'écran de détail (« Expédition introuvable »). */
  get(reference: string): Observable<ShipmentDetail> {
    return this.http.get<ShipmentDetail>(`/api/shipments/${encodeURIComponent(reference)}`, {
      context: new HttpContext().set(HANDLES_ERRORS, true),
    });
  }

  /** Les erreurs sont affichées sous le formulaire de création. */
  create(shipment: CreateShipment): Observable<Shipment> {
    return this.http.post<Shipment>('/api/shipments', shipment, {
      context: new HttpContext().set(HANDLES_ERRORS, true),
    });
  }
}
