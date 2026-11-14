import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { parseProjectStats } from './project-stats';

/** Vue d'ensemble de `/projet` : pitch et liens vers les autres pages et chiffres du projet. */
@Component({
  selector: 'app-project-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styleUrl: './overview.scss',
  template: `
    <h2>Suivre un colis de bout en bout</h2>
    <p class="pitch">
      Une plateforme de suivi de flux logistiques : des scanners et des fichiers CSV envoient des
      scans, les expéditions changent de statut, les retards sont détectés.
    </p>
    <p class="pitch">
      Trois services Spring Boot (expéditions, ingestion, lecture de bons de livraison par Claude)
      échangent par REST et Kafka ; ce front Angular est servi par nginx.
    </p>
    <p class="pitch">
      Les règles de passage d’un statut à l’autre, les anomalies et les choix d’architecture sont
      expliqués dans les pages ci-dessous.
    </p>
    <ul class="links">
      <li>
        <a routerLink="/projet/architecture"
          ><strong>Architecture</strong><span>Les services et leurs échanges</span></a
        >
      </li>
      <li>
        <a routerLink="/projet/statuts"
          ><strong>Statuts</strong><span>Le cycle de vie d’une expédition</span></a
        >
      </li>
      <li>
        <a routerLink="/projet/decisions"
          ><strong>Décisions</strong><span>Les choix faits et leurs raisons</span></a
        >
      </li>
      <li>
        <a routerLink="/projet/ecrans"
          ><strong>Écrans</strong><span>L’application en images</span></a
        >
      </li>
    </ul>
    @if (stats(); as s) {
      <section class="stats" data-testid="project-stats" aria-labelledby="stats-title">
        <h3 id="stats-title">Le projet en chiffres</h3>
        <dl>
          @for (figure of s.figures; track figure.label) {
            <div>
              <dt>{{ figure.label }}</dt>
              <dd>{{ figure.value.toLocaleString('fr-FR') }}</dd>
            </div>
          }
        </dl>
        @if (s.generatedOn || s.commit) {
          <footer>
            Chiffres générés
            @if (s.generatedOn) {
              le {{ s.generatedOn }}
            }
            @if (s.commit) {
              (commit <code>{{ s.commit }}</code>)
            }
          </footer>
        }
      </section>
    }
  `,
})
export class Overview {
  /** Chiffres lus dans `/project-stats.json` (généré au déploiement) ; `null` : absent ou illisible, bloc masqué. */
  protected readonly stats = toSignal(
    inject(HttpClient)
      .get<unknown>('/project-stats.json')
      .pipe(
        map(parseProjectStats),
        catchError(() => of(null)),
      ),
    { initialValue: null },
  );
}
