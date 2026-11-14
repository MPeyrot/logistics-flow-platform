import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DECISIONS, JOURNAL_URL, issueUrl } from './decisions-data';

/** Page `/projet/decisions` : une sélection de décisions, pour un lecteur qui découvre le projet. */
@Component({
  selector: 'app-project-decisions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './decisions.scss',
  templateUrl: './decisions.html',
})
export class Decisions {
  protected readonly decisions = DECISIONS;
  protected readonly journalUrl = JOURNAL_URL;
  protected readonly issueUrl = issueUrl;

  /** Les textes sont de la prose avec des `code` entre accents graves : on les découpe pour le gabarit. */
  protected parts(text: string): { code: boolean; text: string }[] {
    return text.split('`').map((t, i) => ({ code: i % 2 === 1, text: t }));
  }
}
