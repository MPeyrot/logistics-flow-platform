import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ANOMALY_TYPE_LABELS, SHIPMENT_STATUS_LABELS } from '../../core/labels';
import { DltService } from '../admin/dlt/dlt.service';
import { formatDateTime } from '../shipments/format-date';
import type { Shipment } from '../shipments/shipment.model';
import { ShipmentService } from '../shipments/shipment.service';
import {
  STATUSES,
  countByStatus,
  countByType,
  dueWithin24h,
  latestAnomalies,
  type OpenAnomalyRow,
} from './dashboard-stats';

@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButton],
  styleUrl: './dashboard.scss',
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly shipmentService = inject(ShipmentService);
  private readonly dltService = inject(DltService);
  private readonly http = inject(HttpClient);

  protected readonly isAdmin = inject(AuthService).hasRole('ADMIN');
  protected readonly statusLabels = SHIPMENT_STATUS_LABELS;
  protected readonly typeLabels = ANOMALY_TYPE_LABELS;
  protected readonly statuses = STATUSES;
  protected readonly formatDate = formatDateTime;

  protected readonly loading = signal(false);
  protected readonly shipments = signal<Shipment[] | null>(null);
  protected readonly anomalies = signal<OpenAnomalyRow[] | null>(null);
  protected readonly dltCount = signal<number | null>(null);

  protected readonly byStatus = computed(() => countByStatus(this.shipments() ?? []));
  protected readonly total = computed(() => (this.shipments() ?? []).length);
  protected readonly byType = computed(() => countByType(this.anomalies() ?? []));
  protected readonly latest = computed(() => latestAnomalies(this.anomalies() ?? []));
  protected readonly due = computed(() => dueWithin24h(this.shipments() ?? [], new Date()));

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.shipmentService.list().subscribe({
      next: (list) => {
        this.shipments.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.http
      .get<OpenAnomalyRow[]>('/api/anomalies', { params: { open: true } })
      .subscribe({ next: (rows) => this.anomalies.set(rows), error: () => undefined });
    if (this.isAdmin) {
      this.dltService
        .list()
        .subscribe({ next: (list) => this.dltCount.set(list.length), error: () => undefined });
    }
  }

  protected share(count: number): number {
    return this.total() === 0 ? 0 : (count / this.total()) * 100;
  }
}
