import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { EMPTY, Subscription, catchError, switchMap, takeWhile, tap, timer } from 'rxjs';
import type { ReplayProgress } from './dlt.model';
import { DltService } from './dlt.service';

export const REPLAY_ID_KEY = 'dlt.replayAllId';
const POLL_MS = 1000;

/**
 * Suivi d'un « tout rejouer » : l'identifiant est gardé dans `localStorage` pour reprendre après
 * un rechargement, et effacé quand le serveur dit `finished` (ou ne connaît plus l'identifiant).
 * À fournir au niveau de l'écran : le suivi s'arrête avec lui.
 */
@Injectable()
export class ReplayTracker implements OnDestroy {
  private readonly service = inject(DltService);
  private subscription: Subscription | undefined;
  private readonly state = signal<ReplayProgress | null>(null);

  readonly progress = this.state.asReadonly();

  /** Lance « tout rejouer » puis suit son avancement. */
  start(): void {
    this.service.replayAll().subscribe((job) => {
      localStorage.setItem(REPLAY_ID_KEY, job.id);
      this.state.set(job);
      this.follow(job.id);
    });
  }

  /** Reprend le suivi de l'identifiant gardé, s'il y en a un. */
  resume(): void {
    const id = localStorage.getItem(REPLAY_ID_KEY);
    if (id) {
      this.follow(id);
    }
  }

  stop(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private follow(id: string): void {
    this.stop();
    this.subscription = timer(0, POLL_MS)
      .pipe(
        switchMap(() =>
          this.service.progress(id).pipe(
            catchError((error: unknown) => {
              if (error instanceof HttpErrorResponse && error.status === 404) {
                // Le serveur ne connaît plus ce rejeu (redémarrage) : plus rien à suivre.
                localStorage.removeItem(REPLAY_ID_KEY);
                this.state.set(null);
                this.stop();
              }
              return EMPTY;
            }),
          ),
        ),
        tap((job) => {
          this.state.set(job);
          if (job.finished) {
            localStorage.removeItem(REPLAY_ID_KEY);
          }
        }),
        takeWhile((job) => !job.finished),
      )
      .subscribe();
  }
}
