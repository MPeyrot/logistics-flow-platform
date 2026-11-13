import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
  private readonly reference: string = inject(ActivatedRoute).snapshot.paramMap.get('reference') ?? '';

  protected readonly statusLabels = SHIPMENT_STATUS_LABELS;
  protected readonly scanLabels = SCAN_TYPE_LABELS;
  protected readonly anomalyLabels = ANOMALY_TYPE_LABELS;
  protected readonly formatDate = formatDateTime;

  protected readonly shipment = signal<ShipmentDetail | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.service.get(this.reference).subscribe({
      next: (detail) => this.shipment.set(detail),
      error: (err: unknown) =>
        this.error.set(
          err instanceof HttpErrorResponse
            ? err.status === 404
              ? 'Expédition introuvable'
              : describeError(err)
            : 'Une erreur est survenue.',
        ),
    });
  }
}
