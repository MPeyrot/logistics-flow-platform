import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
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

  constructor() {
    // Un message d'erreur ne survit pas à un changement de page.
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationStart))
      .subscribe(() => this.messages.clear());
  }
}
