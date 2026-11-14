import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import type { Subscription } from 'rxjs';
import { ANOMALY_TYPE_LABELS } from '../../core/labels';
import { formatDateTime } from '../shipments/format-date';
import { AnomalyService, type AnomalyRow } from './anomaly.service';

@Component({
  selector: 'app-anomalies',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButton, MatTabGroup, MatTab],
  styleUrl: './anomalies.scss',
  templateUrl: './anomalies.html',
})
export class Anomalies {
  private readonly anomalyService = inject(AnomalyService);

  protected readonly typeLabels = ANOMALY_TYPE_LABELS;
  protected readonly formatDate = formatDateTime;
  protected readonly tab = signal(0);
  protected readonly rows = signal<AnomalyRow[]>([]);
  protected readonly loading = signal(false);
  protected readonly loaded = signal(false);
  private pending?: Subscription;

  constructor() {
    this.refresh();
  }

  protected get closed(): boolean {
    return this.tab() === 1;
  }

  protected select(index: number): void {
    this.tab.set(index);
    this.refresh();
  }

  protected refresh(): void {
    // La requête de l'onglet quitté est annulée : sa réponse tardive ne doit rien afficher.
    this.pending?.unsubscribe();
    this.loading.set(true);
    this.loaded.set(false);
    this.pending = this.anomalyService.list(!this.closed).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
