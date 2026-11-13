import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { describeError, problemDetail, retryAfterSeconds } from '../../core/http/error-messages';
import { SHIPMENT_STATUS_LABELS } from '../../core/labels';
import { RetryCountdown } from '../../shared/retry-countdown';
import { CreateShipmentDialog } from '../shipments/create-shipment-dialog';
import { formatDateTime } from '../shipments/format-date';
import type { DeliveryNoteReading } from './delivery-note.model';
import { DeliveryNoteService } from './delivery-note.service';
import { checkFile } from './file-check';
import { prefillFromNote } from './prefill';

const cost = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });

/** Texte d'une erreur de l'extraction : 415 et 422 disent ce qui ne va pas avec le fichier. */
function describeExtractError(err: HttpErrorResponse): string {
  switch (err.status) {
    case 415:
      return problemDetail(err) ?? 'Ce type de fichier n’est pas pris en charge.';
    case 422:
      return `Le bon n’a pas pu être lu : ${problemDetail(err) ?? 'document illisible.'}`;
    case 429:
      return 'Trop de demandes de lecture.';
    default:
      return describeError(err);
  }
}

@Component({
  selector: 'app-delivery-notes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButton, RetryCountdown],
  styleUrl: './delivery-notes.scss',
  templateUrl: './delivery-notes.html',
})
export class DeliveryNotes {
  private readonly service = inject(DeliveryNoteService);
  private readonly dialog = inject(MatDialog);

  protected readonly statusLabels = SHIPMENT_STATUS_LABELS;
  protected readonly formatDate = formatDateTime;
  protected readonly reading = signal<DeliveryNoteReading | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly retryAfter = signal<number | null>(null);
  protected readonly dragging = signal(false);

  protected formatCost(value: number): string {
    return `${cost.format(value)} $`;
  }

  protected onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    input.value = '';
    if (file) {
      this.send(file);
    }
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files.item(0);
    if (file && !this.busy()) {
      this.send(file);
    }
  }

  protected busy(): boolean {
    return this.loading() || this.retryAfter() !== null;
  }

  protected createShipment(): void {
    const note = this.reading()?.deliveryNote;
    if (note) {
      this.dialog.open(CreateShipmentDialog, { data: prefillFromNote(note) });
    }
  }

  private send(file: File): void {
    this.reading.set(null);
    this.retryAfter.set(null);
    const problem = checkFile(file);
    if (problem) {
      this.error.set(problem);
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    this.service.extract(file).subscribe({
      next: (reading) => {
        this.reading.set(reading);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err instanceof HttpErrorResponse) {
          this.error.set(describeExtractError(err));
          if (err.status === 429) {
            this.retryAfter.set(retryAfterSeconds(err));
          }
        } else {
          this.error.set('Une erreur est survenue.');
        }
      },
    });
  }
}
