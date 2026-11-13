import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { describeError } from '../../core/http/error-messages';
import {
  ANOMALY_TYPE_LABELS,
  SCAN_TYPE_LABELS,
  SHIPMENT_STATUS_LABELS,
} from '../../core/labels';
import { formatDateTime } from './format-date';
import type { ShipmentDetail } from './shipment.model';
import { ShipmentService } from './shipment.service';

@Component({
  selector: 'app-shipment-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styleUrl: './shipment-detail.scss',
  templateUrl: './shipment-detail.html',
})
export class ShipmentDetailPage {
  private readonly service = inject(ShipmentService);
  protected readonly statusLabels = SHIPMENT_STATUS_LABELS;
  protected readonly scanLabels = SCAN_TYPE_LABELS;
  protected readonly anomalyLabels = ANOMALY_TYPE_LABELS;
  protected readonly formatDate = formatDateTime;

  /** Se rejoue à chaque changement de référence : le composant est réutilisé par le routeur. */
  private readonly state = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((params) => params.get('reference') ?? ''),
      switchMap((reference) =>
        this.service.get(reference).pipe(
          map((detail) => ({ shipment: detail, error: null as string | null })),
          catchError((err: unknown) =>
            of({
              shipment: null,
              error:
                err instanceof HttpErrorResponse
                  ? err.status === 404
                    ? 'Expédition introuvable'
                    : describeError(err)
                  : 'Une erreur est survenue.',
            }),
          ),
          startWith({ shipment: null, error: null }),
        ),
      ),
    ),
    { initialValue: { shipment: null, error: null } },
  );

  protected readonly shipment = computed<ShipmentDetail | null>(() => this.state().shipment);
  protected readonly error = computed<string | null>(() => this.state().error);
}
