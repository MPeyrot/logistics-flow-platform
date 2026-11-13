import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { describeError } from '../../../core/http/error-messages';
import { formatDateTime } from '../../shipments/format-date';
import { ConfirmDialog, type ConfirmData } from '../confirm-dialog';
import { prettyJson, shortClassName, type DeadLetter } from './dlt.model';
import { DltService } from './dlt.service';
import { ReplayTracker } from './replay-tracker';

const rowKey = (letter: DeadLetter) => `${letter.partition}/${letter.offset}`;

@Component({
  selector: 'app-dlt',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButton, MatProgressBar],
  providers: [ReplayTracker],
  styleUrl: './dlt.scss',
  templateUrl: './dlt.html',
})
export class Dlt {
  private readonly service = inject(DltService);
  private readonly dialog = inject(MatDialog);
  protected readonly tracker = inject(ReplayTracker);

  protected readonly formatDate = formatDateTime;
  protected readonly shortClass = shortClassName;
  protected readonly pretty = prettyJson;
  protected readonly running = computed(() => {
    const p = this.tracker.progress();
    return p !== null && !p.finished;
  });
  protected readonly letters = signal<DeadLetter[]>([]);
  protected readonly loaded = signal(false);
  protected readonly loading = signal(false);
  protected readonly expanded = signal<ReadonlySet<string>>(new Set());
  /** Message sous une ligne : « Déjà rejoué » (409) ou l'erreur du rejeu. */
  protected readonly notes = signal<Record<string, string>>({});

  constructor() {
    this.refresh();
    this.tracker.resume();
    // Un « tout rejouer » qui passe de « en cours » à « terminé » : la liste change (messages
    // rejoués). Un rejeu repris déjà terminé ne change rien : la liste vient d'être lue.
    let wasRunning = false;
    effect(() => {
      const progress = this.tracker.progress();
      const finished = progress?.finished === true;
      if (finished && wasRunning) {
        untracked(() => this.refresh());
      }
      wasRunning = progress !== null && !finished;
    });
  }

  protected refresh(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.letters.set(list);
        this.notes.set({});
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected key = rowKey;

  protected isExpanded(letter: DeadLetter): boolean {
    return this.expanded().has(rowKey(letter));
  }

  protected toggleExpanded(letter: DeadLetter): void {
    const next = new Set(this.expanded());
    const key = rowKey(letter);
    if (!next.delete(key)) {
      next.add(key);
    }
    this.expanded.set(next);
  }

  protected replay(letter: DeadLetter): void {
    this.confirm({
      title: 'Rejouer le message',
      message: `Rejouer le message ${rowKey(letter)} vers scan-events ?`,
      confirm: 'Rejouer',
    }).subscribe((ok) => {
      if (ok) {
        this.doReplay(letter);
      }
    });
  }

  protected replayAll(): void {
    this.confirm({
      title: 'Tout rejouer',
      message: 'Rejouer tous les messages pas encore rejoués vers scan-events ?',
      confirm: 'Tout rejouer',
    }).subscribe((ok) => {
      if (ok) {
        this.tracker.start();
      }
    });
  }

  protected percent(published: number, replayed: number, finished: boolean): number {
    if (finished) {
      return 100;
    }
    return replayed > 0 ? Math.round((published / replayed) * 100) : 0;
  }

  private confirm(data: ConfirmData) {
    return this.dialog
      .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, { data })
      .afterClosed();
  }

  private doReplay(letter: DeadLetter): void {
    const key = rowKey(letter);
    this.service.replay(letter.partition, letter.offset).subscribe({
      next: (updated) =>
        this.letters.update((list) => list.map((l) => (rowKey(l) === key ? updated : l))),
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.letters.update((list) =>
            list.map((l) => (rowKey(l) === key ? { ...l, replayed: true } : l)),
          );
          this.notes.update((n) => ({ ...n, [key]: 'Déjà rejoué' }));
        } else {
          this.notes.update((n) => ({
            ...n,
            [key]:
              err instanceof HttpErrorResponse ? describeError(err) : 'Une erreur est survenue.',
          }));
        }
      },
    });
  }
}
