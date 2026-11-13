import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { Router } from '@angular/router';
import { describeError } from '../../core/http/error-messages';
import { ShipmentService } from './shipment.service';

/** Valeurs initiales du dialogue (par exemple depuis un bon de livraison). */
export interface ShipmentPrefill {
  reference?: string;
  origin?: string;
  destination?: string;
  expectedDeliveryAt?: Date;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Valeur d'un `<input type="datetime-local">` : date et heure locales, à la minute. */
export function toLocalInput(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

@Component({
  selector: 'app-create-shipment-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatFormField,
    MatLabel,
    MatInput,
    MatButton,
  ],
  template: `
    <h2 mat-dialog-title>Créer une expédition</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="fields">
        <mat-form-field>
          <mat-label>Référence</mat-label>
          <input matInput formControlName="reference" maxlength="50" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Origine</mat-label>
          <input matInput formControlName="origin" maxlength="100" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Destination</mat-label>
          <input matInput formControlName="destination" maxlength="100" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Livraison prévue</mat-label>
          <input matInput type="datetime-local" formControlName="expectedDeliveryAt" />
        </mat-form-field>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" mat-dialog-close>Annuler</button>
        <button matButton="filled" type="submit" [disabled]="form.invalid || sending()">Créer</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .fields {
      display: flex;
      flex-direction: column;
      min-width: min(24rem, 80vw);
    }
    .error {
      color: var(--mat-sys-error);
      margin: 0;
    }
  `,
})
export class CreateShipmentDialog {
  private readonly service = inject(ShipmentService);
  private readonly router = inject(Router);
  private readonly dialogRef = inject<MatDialogRef<CreateShipmentDialog>>(MatDialogRef);
  private readonly prefill = inject<ShipmentPrefill | null>(MAT_DIALOG_DATA, { optional: true });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    reference: [this.prefill?.reference ?? '', Validators.required],
    origin: [this.prefill?.origin ?? '', Validators.required],
    destination: [this.prefill?.destination ?? '', Validators.required],
    expectedDeliveryAt: [
      this.prefill?.expectedDeliveryAt ? toLocalInput(this.prefill.expectedDeliveryAt) : '',
      Validators.required,
    ],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.service
      .create({
        reference: value.reference.trim(),
        origin: value.origin.trim(),
        destination: value.destination.trim(),
        expectedDeliveryAt: new Date(value.expectedDeliveryAt).toISOString(),
      })
      .subscribe({
        next: (created) => {
          this.dialogRef.close();
          void this.router.navigate(['/shipments', created.reference]);
        },
        error: (err: unknown) => {
          this.sending.set(false);
          this.error.set(err instanceof HttpErrorResponse ? describeError(err) : 'Une erreur est survenue.');
        },
      });
  }
}
