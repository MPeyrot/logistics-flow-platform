import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { homeUrl } from '../../core/auth/guards';

@Component({
  selector: 'app-access-denied',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <main class="page">
      <h2>Accès refusé</h2>
      <p>Votre rôle ne permet pas d’ouvrir cette page.</p>
      <a [routerLink]="home()">Retour à l’accueil</a>
    </main>
  `,
  styles: `
    .page {
      max-width: 40rem;
      margin: 2rem auto;
      padding: 0 1rem;
    }
  `,
})
export class AccessDenied {
  private readonly auth = inject(AuthService);
  protected readonly home = computed(() => {
    const session = this.auth.session();
    return session ? homeUrl(session) : '/login';
  });
}
