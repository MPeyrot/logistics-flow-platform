import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Vue d'ensemble de `/projet` : pitch et liens vers les autres pages (les chiffres viendront d'un autre ticket). */
@Component({
  selector: 'app-project-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styleUrl: './overview.scss',
  template: `
    <h2>Suivre un colis de bout en bout</h2>
    <p class="pitch">
      Une plateforme de suivi de flux logistiques : des scanners et des fichiers CSV envoient des scans, les
      expéditions changent de statut, les retards sont détectés.
    </p>
    <p class="pitch">
      Trois services Spring Boot (expéditions, ingestion, lecture de bons de livraison par Claude) échangent par REST
      et Kafka ; ce front Angular est servi par nginx.
    </p>
    <p class="pitch">
      Les règles de passage d’un statut à l’autre, les anomalies et les choix d’architecture sont expliqués dans les
      pages ci-dessous.
    </p>
    <ul class="links">
      <li>
        <a routerLink="/projet/architecture"><strong>Architecture</strong><span>Les services et leurs échanges</span></a>
      </li>
      <li>
        <a routerLink="/projet/statuts"><strong>Statuts</strong><span>Le cycle de vie d’une expédition</span></a>
      </li>
      <li>
        <a routerLink="/projet/decisions"><strong>Décisions</strong><span>Les choix faits et leurs raisons</span></a>
      </li>
      <li>
        <a routerLink="/projet/ecrans"><strong>Écrans</strong><span>L’application en images</span></a>
      </li>
    </ul>
  `,
})
export class Overview {}
