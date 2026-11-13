import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';

/**
 * Compte à rebours « Réessayez dans X s » après un 429 (`Retry-After`). Réutilisable : l'écran
 * passe les secondes, désactive son bouton d'envoi tant qu'il est affiché et le réactive sur
 * `(finished)`. Pas de nouvel essai automatique.
 */
@Component({
  selector: 'app-retry-countdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (remaining() > 0) {
      <p class="retry-countdown" role="status">Réessayez dans {{ remaining() }} s</p>
    }
  `,
})
export class RetryCountdown {
  readonly seconds = input.required<number>();
  readonly finished = output<void>();

  protected readonly remaining = signal(0);

  constructor() {
    effect((onCleanup) => {
      let left = Math.max(0, Math.ceil(this.seconds()));
      this.remaining.set(left);
      if (left === 0) {
        return;
      }
      const timer = setInterval(() => {
        left -= 1;
        this.remaining.set(left);
        if (left <= 0) {
          clearInterval(timer);
          this.finished.emit();
        }
      }, 1000);
      onCleanup(() => clearInterval(timer));
    });
  }
}
