import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { describeError, retryAfterSeconds } from '../../core/http/error-messages';
import { SCAN_TYPE_LABELS, type ScanType } from '../../core/labels';
import { RetryCountdown } from '../../shared/retry-countdown';
import { formatDateTime } from '../shipments/format-date';
import { EventIdKeeper } from './event-id';
import { REASON_MAX, allowsReason, reasonCount, reasonError, reasonToSend } from './scan-rules';
import { ScanService } from './scan.service';

interface SentScan {
  reference: string;
  type: ScanType;
  sentAt: string;
}

const SESSION_LIST_SIZE = 10;

@Component({
  selector: 'app-scan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButton,
    MatFormField,
    MatHint,
    MatLabel,
    MatInput,
    RetryCountdown,
  ],
  styleUrl: './scan.scss',
  templateUrl: './scan.html',
})
export class Scan {
  private readonly service = inject(ScanService);
  private readonly keeper = new EventIdKeeper();
  private readonly referenceInput = viewChild<ElementRef<HTMLInputElement>>('reference');

  protected readonly site = inject(AuthService).session()?.site ?? null;
  protected readonly typeLabels = SCAN_TYPE_LABELS;
  protected readonly types = Object.keys(SCAN_TYPE_LABELS) as ScanType[];
  protected readonly reasonMax = REASON_MAX;
  protected readonly formatDate = formatDateTime;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    reference: ['', [Validators.required, Validators.maxLength(50)]],
    type: ['PICKED_UP' as ScanType],
    reason: [''],
    occurredAt: [''],
  });

  protected readonly type = signal<ScanType>('PICKED_UP');
  protected readonly reason = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly retryAfter = signal<number | null>(null);
  protected readonly success = signal(false);
  protected readonly recent = signal<SentScan[]>([]);

  protected readonly showReason = computed(() => allowsReason(this.type()));
  protected readonly reasonCount = computed(() => reasonCount(this.reason()));
  protected readonly reasonProblem = computed(() => reasonError(this.type(), this.reason()));

  constructor() {
    afterNextRender(() => this.referenceInput()?.nativeElement.focus());
    this.form.controls.type.valueChanges.subscribe((type) => this.type.set(type));
    this.form.controls.reason.valueChanges.subscribe((reason) => this.reason.set(reason));
  }

  protected canSend(): boolean {
    return (
      this.form.valid &&
      this.site !== null &&
      !this.reasonProblem() &&
      !this.sending() &&
      this.retryAfter() === null
    );
  }

  protected submit(): void {
    if (!this.canSend() || this.site === null) {
      return;
    }
    const value = this.form.getRawValue();
    const reference = value.reference.trim();
    const occurredAt = value.occurredAt ? new Date(value.occurredAt).toISOString() : undefined;
    const reason = reasonToSend(value.type, value.reason);
    const eventId = this.keeper.idFor({ reference, type: value.type, site: this.site, occurredAt, reason });
    this.sending.set(true);
    this.error.set(null);
    this.success.set(false);
    this.service
      .send({
        eventId,
        shipmentReference: reference,
        type: value.type,
        site: this.site,
        ...(occurredAt && { occurredAt }),
        ...(reason !== undefined && { reason }),
      })
      .subscribe({
        next: () => this.onAccepted(reference, value.type),
        error: (err: unknown) => this.onError(err),
      });
  }

  private onAccepted(reference: string, type: ScanType): void {
    this.keeper.accepted();
    this.sending.set(false);
    this.success.set(true);
    this.recent.update((list) =>
      [{ reference, type, sentAt: new Date().toISOString() }, ...list].slice(0, SESSION_LIST_SIZE),
    );
    this.form.reset({ reference: '', type, reason: '', occurredAt: '' });
    this.referenceInput()?.nativeElement.focus();
  }

  private onError(err: unknown): void {
    this.sending.set(false);
    if (err instanceof HttpErrorResponse) {
      this.error.set(describeError(err));
      if (err.status === 429) {
        this.retryAfter.set(retryAfterSeconds(err));
      }
    } else {
      this.error.set('Une erreur est survenue.');
    }
  }
}
