import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  InjectionToken,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, map, of, startWith, switchMap, tap } from 'rxjs';
import { describeError } from '../../core/http/error-messages';
import { ANOMALY_TYPE_LABELS, SCAN_TYPE_LABELS, SHIPMENT_STATUS_LABELS } from '../../core/labels';
import { formatDateTime } from './format-date';
import { ShipmentScanActions, type AcceptedScan } from './shipment-scan-actions';
import type { ShipmentDetail } from './shipment.model';
import { ShipmentService } from './shipment.service';
import { buildTimeline } from './timeline';

/** Rechargement du détail après un scan accepté (202) : le consommateur Kafka agit en différé. */
export const SCAN_POLL = new InjectionToken<{ intervalMs: number; attempts: number }>('SCAN_POLL', {
  factory: () => ({ intervalMs: 1000, attempts: 10 }),
});

@Component({
  selector: 'app-shipment-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ShipmentScanActions],
  styleUrl: './shipment-detail.scss',
  templateUrl: './shipment-detail.html',
})
export class ShipmentDetailPage {
  private readonly service = inject(ShipmentService);
  protected readonly statusLabels = SHIPMENT_STATUS_LABELS;
  protected readonly scanLabels = SCAN_TYPE_LABELS;
  protected readonly anomalyLabels = ANOMALY_TYPE_LABELS;
  protected readonly formatDate = formatDateTime;
  private readonly poll = inject(SCAN_POLL);
  private generation = 0;
  private readonly fresh = signal<ShipmentDetail | null>(null);
  protected readonly waiting = signal(false);
  protected readonly stale = signal(false);

  /** Se rejoue à chaque changement de référence : le composant est réutilisé par le routeur. */
  private readonly state = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((params) => params.get('reference') ?? ''),
      switchMap((reference) =>
        this.service.get(reference).pipe(
          tap(() => {
            this.generation++;
            this.fresh.set(null);
            this.waiting.set(false);
            this.stale.set(false);
          }),
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

  protected readonly shipment = computed<ShipmentDetail | null>(
    () => this.fresh() ?? this.state().shipment,
  );
  protected readonly timeline = computed(() => {
    const s = this.shipment();
    return s ? buildTimeline(s.scans, s.anomalies) : [];
  });
  protected readonly error = computed<string | null>(() => this.state().error);

  /** Recharge le détail jusqu'à ce que le scan se voie (statut changé ou scan présent), sinon l'écrit. */
  protected async onAccepted(scan: AcceptedScan): Promise<void> {
    const reference = this.shipment()?.reference;
    if (!reference) {
      return;
    }
    const generation = ++this.generation;
    this.waiting.set(true);
    this.stale.set(false);
    for (let i = 0; i < this.poll.attempts; i++) {
      await new Promise((resolve) => setTimeout(resolve, this.poll.intervalMs));
      if (generation !== this.generation) {
        return;
      }
      try {
        const detail = await firstValueFrom(this.service.get(reference));
        if (generation !== this.generation) {
          return;
        }
        this.fresh.set(detail);
        if (detail.status !== scan.status || detail.scans.some((s) => s.eventId === scan.eventId)) {
          this.waiting.set(false);
          return;
        }
      } catch {
        // Erreur passagère : on réessaie au tour suivant.
      }
    }
    this.waiting.set(false);
    this.stale.set(true);
  }
}
