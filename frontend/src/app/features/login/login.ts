import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, UnreadableTokenError } from '../../core/auth/auth.service';
import { canAccess, homeUrl } from '../../core/auth/guards';
import { describeError, retryAfterSeconds } from '../../core/http/error-messages';
import { RetryCountdown } from '../../shared/retry-countdown';

/** Un `returnUrl` n'est suivi que s'il reste dans l'application. */
function safeReturnUrl(url: string | null): string | null {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : null;
}

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatButton, RetryCountdown, RouterLink],
  styleUrl: './login.scss',
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly expired = this.route.snapshot.queryParamMap.has('expired');
  protected readonly error = signal<string | null>(null);
  protected readonly waitSeconds = signal(0);
  protected readonly sending = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.sending() || this.waitSeconds() > 0) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => {
        const session = this.auth.session();
        if (!session) {
          return;
        }
        const returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
        // Un `returnUrl` d'un autre rôle (session précédente expirée) mènerait à « Accès refusé ».
        const target =
          returnUrl && canAccess(this.router, session, returnUrl) ? returnUrl : homeUrl(session);
        void this.router.navigateByUrl(target);
      },
      error: (err: unknown) => {
        this.sending.set(false);
        this.form.controls.password.reset();
        this.error.set(this.messageFor(err));
      },
    });
  }

  private messageFor(err: unknown): string {
    if (err instanceof UnreadableTokenError) {
      return 'Réponse de connexion illisible. Réessayez.';
    }
    if (!(err instanceof HttpErrorResponse)) {
      return 'Une erreur est survenue.';
    }
    if (err.status === 401) {
      return 'Identifiant ou mot de passe incorrect';
    }
    if (err.status === 429) {
      this.waitSeconds.set(retryAfterSeconds(err) ?? 0);
      return 'Trop de tentatives de connexion.';
    }
    return describeError(err);
  }
}
