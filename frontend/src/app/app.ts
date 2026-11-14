import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { NavigationEnd, NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { MessageService } from './core/messages/message.service';
import { Header } from './features/header/header';

@Component({
  imports: [RouterOutlet, Header, MatIconButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly messages = inject(MessageService);
  protected readonly auth = inject(AuthService);

  /** Sur `/projet`, la barre du projet remplace l'en-tête de l'application. */
  protected readonly onProject = signal(isProjectUrl(inject(Location).path()));

  constructor() {
    const router = inject(Router);
    // Un message d'erreur ne survit pas à un changement de page.
    router.events
      .pipe(filter((e) => e instanceof NavigationStart))
      .subscribe(() => this.messages.clear());
    router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe((e) => this.onProject.set(isProjectUrl(e.urlAfterRedirects)));
  }
}

function isProjectUrl(url: string): boolean {
  return /^\/projet(?:[/?#]|$)/.test(url);
}
