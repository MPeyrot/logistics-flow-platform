import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatAnchor } from '@angular/material/button';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

export const PROJECT_PAGES = [
  { label: 'Vue d’ensemble', path: '/projet' },
  { label: 'Architecture', path: '/projet/architecture' },
  { label: 'Statuts', path: '/projet/statuts' },
  { label: 'Décisions', path: '/projet/decisions' },
  { label: 'Écrans', path: '/projet/ecrans' },
] as const;

/** Enveloppe des pages `/projet` : barre du haut propre à l'espace public et mention. */
@Component({
  selector: 'app-project-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatAnchor, RouterLink, RouterLinkActive, RouterOutlet],
  styleUrl: './project-shell.scss',
  templateUrl: './project-shell.html',
})
export class ProjectShell {
  protected readonly auth = inject(AuthService);
  protected readonly pages = PROJECT_PAGES;
}
