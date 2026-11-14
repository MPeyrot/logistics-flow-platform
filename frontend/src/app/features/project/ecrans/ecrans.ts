import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface Screen {
  /** Nom de fichier sans thème ni extension (`public/screenshots/<file>-light.jpg`). */
  file: string;
  title: string;
  caption: string;
  alt: string;
}

/** Page `/projet/ecrans` : captures des écrans connectés, générées par `npm run screenshots`. */
@Component({
  selector: 'app-project-ecrans',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styleUrl: './ecrans.scss',
  template: `
    <h2>Les écrans de l’application</h2>
    <p class="intro">
      Ces écrans demandent une connexion ; voici des captures, prises sur des données fictives.
      Selon le thème de votre appareil, clair ou sombre, la capture suit.
    </p>
    <p class="intro">
      <a routerLink="/login">Se connecter pour essayer</a>
    </p>
    <div class="grid">
      @for (screen of screens; track screen.file; let first = $first) {
        <figure>
          <picture>
            <source media="(prefers-color-scheme: dark)" [srcset]="'/screenshots/' + screen.file + '-dark.jpg'" />
            <img
              [src]="'/screenshots/' + screen.file + '-light.jpg'"
              [alt]="screen.alt"
              width="1280"
              height="800"
              [attr.loading]="first ? 'eager' : 'lazy'"
            />
          </picture>
          <figcaption>
            <strong>{{ screen.title }}</strong>
            <span>{{ screen.caption }}</span>
          </figcaption>
        </figure>
      }
    </div>
    <p class="intro">
      <a routerLink="/login">Se connecter pour essayer</a>
    </p>
  `,
})
export class ProjectEcrans {
  protected readonly screens: readonly Screen[] = [
    {
      file: 'tableau-de-bord',
      title: 'Tableau de bord',
      caption: 'Les expéditions par statut, les retards et les anomalies ouvertes, d’un coup d’œil.',
      alt: 'Tableau de bord : compteurs d’expéditions par statut, livraisons proches et anomalies ouvertes.',
    },
    {
      file: 'expeditions',
      title: 'Liste des expéditions',
      caption: 'Chaque expédition avec son statut, sa livraison prévue et ses anomalies ouvertes.',
      alt: 'Tableau des expéditions avec pastilles de statut et d’anomalie.',
    },
    {
      file: 'detail',
      title: 'Détail d’une expédition',
      caption: 'La frise des scans, le motif d’un échec de livraison et l’anomalie de retard.',
      alt: 'Détail d’une expédition en échec de livraison : frise des scans et anomalie de retard.',
    },
    {
      file: 'dlt',
      title: 'Messages en échec (DLT)',
      caption: 'Les scans que le consommateur n’a pas pu traiter, avec leur erreur, et le rejeu par l’administrateur.',
      alt: 'Liste des messages de la file des échecs, avec l’erreur et un bouton de rejeu.',
    },
    {
      file: 'comptes',
      title: 'Comptes',
      caption: 'Les comptes, leurs rôles (administrateur, opérateur, scanner) et leur activation.',
      alt: 'Liste des comptes avec rôle, site et état actif ou désactivé.',
    },
    {
      file: 'scan',
      title: 'Scan',
      caption: 'La saisie d’un scan par un scanner, rattaché à son site.',
      alt: 'Formulaire de scan : référence, type de scan et site du jeton.',
    },
    {
      file: 'import-csv',
      title: 'Import CSV',
      caption: 'Un fichier de scans importé d’un coup, avec le rapport des lignes rejetées.',
      alt: 'Rapport d’import CSV : 120 lignes, 117 publiées, 3 rejetées avec leur erreur.',
    },
    {
      file: 'extraction',
      title: 'Lecture d’un bon de livraison',
      caption: 'Un bon déposé, lu par Claude : champs extraits, coût et jetons, expédition correspondante.',
      alt: 'Bon de livraison lu : expéditeur, destinataire, colis, coût de la lecture et expédition liée.',
    },
  ];
}
