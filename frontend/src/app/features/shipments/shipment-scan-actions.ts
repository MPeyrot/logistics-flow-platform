import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { transitionsFrom } from '../../core/shipment-transitions';
import { describeError, retryAfterSeconds } from '../../core/http/error-messages';
import type { ScanType, ShipmentStatus } from '../../core/labels';
import { RetryCountdown } from '../../shared/retry-countdown';
import { EventIdKeeper } from '../scan/event-id';
import {
  REASON_MAX,
  allowsReason,
  reasonCount,
  reasonError,
  reasonToSend,
} from '../scan/scan-rules';
import { ScanService } from '../scan/scan.service';
import { defaultSite } from './scan-site';

/** Libellés des boutons du détail (un par transition permise). */
export const ACTION_LABELS: Record<ScanType, string> = {
  PICKED_UP: 'Prise en charge',
  ARRIVED_AT_SITE: 'Arrivée au site',
  OUT_FOR_DELIVERY: 'En livraison',
  DELIVERED: 'Livrée',
  DELIVERY_FAILED: 'Échec de livraison',
};

export interface AcceptedScan {
  eventId: string;
  status: ShipmentStatus;
}

export interface ScannableShipment {
  reference: string;
  status: ShipmentStatus;
  origin: string;
  destination: string;
}

/** Boutons de scan d'une expédition : une action par transition permise depuis son statut. */
@Component({
  selector: 'app-shipment-scan-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatFormField,
    MatHint,
    MatLabel,
    MatInput,
    RetryCountdown,
  ],
  template: `
    @if (transitions().length > 0) {
      <section class="actions panel" aria-label="Envoyer un scan">
        <h3>Envoyer un scan</h3>
        <form [formGroup]="form" (ngSubmit)="$event.preventDefault()" class="fields">
          <mat-form-field>
            <mat-label>Site du scan</mat-label>
            <input matInput formControlName="site" maxlength="100" autocomplete="off" />
          </mat-form-field>
          @if (hasFailure()) {
            <mat-form-field>
              <mat-label>Motif de l’échec (facultatif)</mat-label>
              <textarea matInput formControlName="reason" rows="2"></textarea>
              <mat-hint align="end" [class.over]="count() > reasonMax"
                >{{ count() }} / {{ reasonMax }}</mat-hint
              >
            </mat-form-field>
            @if (reasonProblem(); as problem) {
              <p class="error" role="alert">{{ problem }}</p>
            }
          }
          <div class="buttons">
            @for (t of transitions(); track t.scan) {
              <button
                matButton="filled"
                type="button"
                [disabled]="!canSend(t.scan)"
                (click)="send(t.scan)"
              >
                {{ labels[t.scan] }}
              </button>
            }
          </div>
        </form>
        <p class="note">
          Saisie manuelle par le bureau, ouverte aux opérateurs pour les besoins de la démo.
        </p>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        @if (retryAfter() !== null) {
          <app-retry-countdown [seconds]="retryAfter()!" (finished)="retryAfter.set(null)" />
        }
      </section>
    }
  `,
})
export class ShipmentScanActions {
  readonly shipment = input.required<ScannableShipment>();
  /** Le serveur a accepté le scan (202) : l'écran attend le changement de statut. */
  readonly accepted = output<AcceptedScan>();
  /** Un scan est en attente de son effet : boutons bloqués. */
  readonly waiting = input(false);

  private readonly service = inject(ScanService);
  private readonly keeper = new EventIdKeeper();

  protected readonly labels = ACTION_LABELS;
  protected readonly reasonMax = REASON_MAX;
  protected readonly transitions = computed(() => transitionsFrom(this.shipment().status));
  protected readonly hasFailure = computed(() =>
    this.transitions().some((t) => allowsReason(t.scan)),
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    site: ['', [Validators.required, Validators.maxLength(100)]],
    reason: [''],
  });
  private readonly reason = signal('');
  private readonly site = signal('');

  protected readonly count = computed(() => reasonCount(this.reason()));
  protected readonly reasonProblem = computed(() => reasonError('DELIVERY_FAILED', this.reason()));
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly retryAfter = signal<number | null>(null);

  constructor() {
    this.form.controls.reason.valueChanges.subscribe((v) => this.reason.set(v));
    this.form.controls.site.valueChanges.subscribe((v) => this.site.set(v));
    // Site prérempli : origine pour la prise en charge, destination sinon ; refait à chaque changement de statut.
    effect(() => {
      const s = this.shipment();
      const first = this.transitions()[0];
      this.form.controls.site.setValue(first ? defaultSite(first.scan, s) : s.destination);
      this.form.controls.reason.setValue('');
      this.error.set(null);
    });
  }

  protected canSend(type: ScanType): boolean {
    return (
      this.site().trim() !== '' &&
      this.site().length <= 100 &&
      !(allowsReason(type) && this.reasonProblem()) &&
      !this.sending() &&
      !this.waiting() &&
      this.retryAfter() === null
    );
  }

  protected send(type: ScanType): void {
    if (!this.canSend(type)) {
      return;
    }
    const { reference, status } = this.shipment();
    const site = this.form.controls.site.value.trim();
    const reason = reasonToSend(type, this.form.controls.reason.value);
    const eventId = this.keeper.idFor({ reference, type, site, reason });
    this.sending.set(true);
    this.error.set(null);
    this.service
      .send({
        eventId,
        shipmentReference: reference,
        type,
        site,
        ...(reason !== undefined && { reason }),
      })
      .subscribe({
        next: () => {
          this.keeper.accepted();
          this.sending.set(false);
          this.accepted.emit({ eventId, status });
        },
        error: (err: unknown) => {
          this.sending.set(false);
          if (err instanceof HttpErrorResponse) {
            this.error.set(describeError(err));
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
