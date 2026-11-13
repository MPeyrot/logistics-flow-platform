import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/** Page vide des routes pas encore construites : chaque ticket d'écran la remplace dans son `<fonctionnalité>.routes.ts`. */
@Component({
  selector: 'app-placeholder-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<main class="page"><h2>{{ title }}</h2></main>`,
  styles: `
    .page {
      padding: 1rem;
    }
  `,
})
export class PlaceholderPage {
  protected readonly title: string = inject(ActivatedRoute).snapshot.data['title'] ?? '';
}
