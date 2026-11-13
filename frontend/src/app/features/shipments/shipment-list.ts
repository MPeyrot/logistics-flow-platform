import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import {
  ANOMALY_TYPE_LABELS,
  SHIPMENT_STATUS_LABELS,
  type ShipmentStatus,
} from '../../core/labels';
import { CreateShipmentDialog } from './create-shipment-dialog';
import { filterShipments, sortShipments, type SortColumn, type SortState } from './shipment-filter';
import { formatDateTime } from './format-date';
import type { Shipment } from './shipment.model';
import { ShipmentService } from './shipment.service';

@Component({
  selector: 'app-shipment-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, MatButton, MatCheckbox, MatFormField, MatLabel, MatInput],
  styleUrl: './shipment-list.scss',
  templateUrl: './shipment-list.html',
})
export class ShipmentList {
  private readonly service = inject(ShipmentService);
  private readonly dialog = inject(MatDialog);

  protected readonly statusLabels = SHIPMENT_STATUS_LABELS;
  protected readonly anomalyLabels = ANOMALY_TYPE_LABELS;
  protected readonly statuses = Object.keys(SHIPMENT_STATUS_LABELS) as ShipmentStatus[];
  protected readonly formatDate = formatDateTime;
  protected readonly columns: { column: SortColumn; label: string }[] = [
    { column: 'reference', label: 'Référence' },
    { column: 'origin', label: 'Origine' },
    { column: 'destination', label: 'Destination' },
    { column: 'status', label: 'Statut' },
    { column: 'expectedDeliveryAt', label: 'Livraison prévue' },
    { column: 'openAnomalies', label: 'Anomalies ouvertes' },
  ];

  protected readonly shipments = signal<Shipment[]>([]);
  protected readonly loading = signal(false);
  protected readonly loaded = signal(false);
  protected readonly search = signal('');
  protected readonly status = signal<ShipmentStatus | ''>('');
  protected readonly withAnomaly = signal(false);
  protected readonly sort = signal<SortState>({ column: 'reference', direction: '' });

  protected readonly rows = computed(() =>
    sortShipments(
      filterShipments(this.shipments(), {
        search: this.search(),
        status: this.status() || null,
        withAnomaly: this.withAnomaly(),
      }),
      this.sort(),
    ),
  );

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.shipments.set(list);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  /** Même colonne : croissant, décroissant, puis ordre du serveur. */
  protected toggleSort(column: SortColumn): void {
    const { column: current, direction } = this.sort();
    if (current !== column) {
      this.sort.set({ column, direction: 'asc' });
    } else {
      this.sort.set({ column, direction: direction === '' ? 'asc' : direction === 'asc' ? 'desc' : '' });
    }
  }

  protected ariaSort(column: SortColumn): 'ascending' | 'descending' | 'none' {
    const { column: current, direction } = this.sort();
    if (current !== column || direction === '') {
      return 'none';
    }
    return direction === 'asc' ? 'ascending' : 'descending';
  }

  protected openCreate(): void {
    this.dialog.open(CreateShipmentDialog, { data: null });
  }
}
