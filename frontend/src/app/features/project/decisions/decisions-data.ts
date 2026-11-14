/** Décisions marquantes du projet, tirées de `.claude/rules/architecture.md` et des issues. */
export interface Decision {
  id: string;
  title: string;
  /** Date de la décision, format ISO (AAAA-MM-JJ). */
  date: string;
  context: string;
  choice: string;
  rejected: string;
  /** Numéro de l'issue GitHub qui porte la décision. */
  issue: number;
}

export const REPO_URL = 'https://github.com/MPeyrot/logistics-flow-platform';
export const JOURNAL_URL = `${REPO_URL}/blob/main/.claude/rules/architecture.md`;

export const issueUrl = (n: number): string => `${REPO_URL}/issues/${n}`;

export const DECISIONS: readonly Decision[] = [
  {
    id: 'hexagonale',
    title: 'Architecture hexagonale dans chaque service',
    date: '2026-10-31',
    context:
      'Trois services Spring Boot qui parlent à PostgreSQL, Kafka et à l’API Claude. Le risque : une logique métier noyée dans les annotations de framework, donc difficile à tester et à faire évoluer.',
    choice:
      '`domain` sans aucune dépendance Spring, `application` (cas d’usage et ports), `infrastructure` (REST, JPA, Kafka, client Claude). Des règles ArchUnit font échouer le build si une couche importe ce qu’elle ne doit pas, par exemple le SDK Claude hors de `infrastructure/claude`.',
    rejected: 'Couches classiques contrôleur / service / dépôt, où les entités JPA traversent tout le code.',
    issue: 69,
  },
  {
    id: 'kafka-ordre',
    title: 'Kafka entre l’ingestion et les expéditions, ordre garanti par expédition',
    date: '2026-10-31',
    context:
      'Les scans arrivent en continu, parfois en double ou dans le désordre, et l’état d’une expédition dépend de leur ordre.',
    choice:
      '`ingestion-service` valide puis publie sur `scan-events` avec la référence d’expédition comme clé : tous les scans d’une expédition vont dans la même partition, donc dans l’ordre. Le consommateur est idempotent (clé primaire sur l’identifiant du scan). Messages en JSON, schémas versionnés dans `contracts/`.',
    rejected: 'Appel REST direct d’`ingestion-service` vers `shipment-service`, sans tampon ni ordre garanti.',
    issue: 4,
  },
  {
    id: 'jwt',
    title: 'JWT émis par shipment-service, validés partout sans secret partagé',
    date: '2026-11-03',
    context: 'Trois rôles (ADMIN, OPERATOR, SCANNER) et trois services qui doivent s’accorder sur qui appelle.',
    choice:
      '`shipment-service` signe des JWT RS256 et publie sa clé publique sur `/.well-known/jwks.json` ; les autres services les valident avec le resource server de Spring Security. Le claim `aud` vise un service précis, un jeton de service de 5 minutes sert pour l’appel vers `ai-service`. Pas de refresh token : la révocation passe par la désactivation du compte.',
    rejected: 'Un fournisseur d’identité externe, disproportionné ici ; un secret symétrique partagé entre services.',
    issue: 13,
  },
  {
    id: 'retries-bloquantes',
    title: 'Nouvelles tentatives bloquantes plutôt que topics de retentative',
    date: '2026-11-05',
    context:
      'Un scan peut arriver avant la création de son expédition. Il faut réessayer sans perdre l’ordre des scans de cette expédition.',
    choice:
      'Backoff exponentiel dans le `DefaultErrorHandler` (1 s, ×2, plafond 60 s, environ 5 minutes au total), puis envoi vers `scan-events.DLT`. Le consommateur attend sans lire : la partition gèle, c’est le prix assumé pour garder l’ordre.',
    rejected:
      'Des topics de retentative : un message retenté passerait devant les suivants de la même expédition, et l’ordre serait perdu.',
    issue: 36,
  },
  {
    id: 'v2-coupure-nette',
    title: 'Contrat scan-event.v2 : coupure nette, sans champ de version',
    date: '2026-11-05',
    context: 'Cinq statuts et de nouveaux types de scan : le contrat des messages change de façon cassante.',
    choice:
      'Un nouveau schéma v2 dont les types v1 sont un sous-ensemble : un message v1 reste un message v2 valide. Producteur et consommateur changent ensemble, sans champ de version dans les messages.',
    rejected: 'Faire cohabiter deux versions avec un champ de version et du code de conversion dans les consommateurs.',
    issue: 33,
  },
  {
    id: 'anomalies-table',
    title: 'Anomalies LATE et STALLED enregistrées par une tâche planifiée',
    date: '2026-11-05',
    context: 'Détecter une expédition en retard ou sans scan depuis 48 h, et le montrer sans recalcul à chaque lecture.',
    choice:
      'Une tâche dans `shipment-service` écrit les anomalies en table, avec un index partiel qui garantit une seule anomalie ouverte par expédition et par type, même avec plusieurs instances. Clôture automatique : STALLED au scan suivant, LATE à la livraison.',
    rejected: 'Calculer le retard à la volée à chaque lecture, sans historique ni état « ouvert / clos ».',
    issue: 39,
  },
  {
    id: 'stalled-reception',
    title: 'STALLED compté depuis l’heure de réception du scan',
    date: '2026-11-06',
    context:
      '`occurredAt` est la date métier fournie par le client : un scanner resté hors ligne ou un rejeu du DLT envoie des scans anciens, et un scan daté dans le futur bloquerait la détection.',
    choice:
      'STALLED se compte depuis `received_at`, l’heure de réception par `shipment-service` (horloge du service), ou depuis la création sans aucun scan. `occurredAt` reste la date métier affichée.',
    rejected: 'Se fier à `occurredAt` : un scan en retard rouvrait aussitôt l’anomalie qu’il venait de clore.',
    issue: 33,
  },
  {
    id: 'outbox-dlt',
    title: 'Rejeu du DLT par une outbox',
    date: '2026-11-06',
    context:
      'Un ADMIN rejoue des messages de `scan-events.DLT` vers `scan-events`. Écrire en base et publier sur Kafka ne peuvent pas être atomiques ensemble.',
    choice:
      'Le rejeu écrit `dlt_replay` et le message à publier dans `dlt_replay_outbox`, dans la même transaction. Un publieur republie puis marque la ligne après l’accusé de Kafka, un seul à la fois grâce à un verrou consultatif PostgreSQL. Livraison au moins une fois, absorbée par l’idempotence du consommateur. « Tout rejouer » est asynchrone (202 et un suivi).',
    rejected:
      'Publier directement dans la transaction : un échec entre les deux laissait un rejeu enregistré mais jamais publié, ou l’inverse.',
    issue: 41,
  },
  {
    id: 'modele-mesure',
    title: 'Modèle Claude choisi par une mesure : Haiku',
    date: '2026-11-05',
    context: 'Opus était le modèle par défaut de l’extraction de bons de livraison. Est-il nécessaire ?',
    choice:
      'Test sur cinq bons fictifs : Haiku 5.5 donne 33 champs justes sur 33, pour environ 0,0004 $ l’extraction contre environ 0,015 $ avec Opus 5.5. Haiku devient le défaut ; le tarif suit le modèle choisi, et le service refuse de démarrer sans tarif complet.',
    rejected: 'Garder le modèle le plus puissant par réflexe, sans mesure.',
    issue: 9,
  },
  {
    id: 'budget-dollars',
    title: 'Budget quotidien de l’IA en dollars, en plus d’un plafond côté fournisseur',
    date: '2026-11-06',
    context: 'Chaque appel à l’API Claude coûte, et un quota par nombre de requêtes ne dit rien du coût réel.',
    choice:
      '`ai-service` compte le coût de chaque appel (jetons × tarif du modèle) contre un budget de 1 $ par jour UTC, configurable ; atteint, il répond 429 sans appeler le modèle. Un plafond de dépense est aussi posé dans la console du fournisseur. `max-instances=1` sur Cloud Run garde le compteur en mémoire exact.',
    rejected: 'Un simple quota de requêtes par heure, ou un compteur partagé dans une base pour un service qui n’en a pas.',
    issue: 59,
  },
  {
    id: 'import-csv',
    title: 'Import CSV synchrone par Spring Batch, sans base',
    date: '2026-11-07',
    context:
      'Un SCANNER importe un fichier de scans. `ingestion-service` n’a pas de base et, sur Cloud Run, du CPU seulement pendant une requête.',
    choice:
      'Import synchrone : lignes invalides sautées et rapportées, identifiant de scan déterministe (UUID par nom) pour qu’un réimport ne crée aucun doublon, plafonds de taille contrôlés avant toute publication. Dépôt de jobs sans base, un seul import à la fois par instance.',
    rejected: 'Une tâche de fond avec reprise de job : elle aurait demandé une base à un service qui n’en a pas.',
    issue: 76,
  },
  {
    id: 'neon',
    title: 'Neon plutôt que Cloud SQL pour la démo en ligne',
    date: '2026-11-06',
    context:
      'La démo se déploie à la demande et se supprime ensuite : le coût à l’arrêt compte autant que le coût en marche.',
    choice:
      'PostgreSQL sur Neon (offre gratuite, région `aws-eu-central-1`, connexion directe en TLS). Kafka reste sur Confluent Cloud, dont l’offre Basic est gardée après l’essai ; le profil de repli sans Kafka est abandonné.',
    rejected: 'Cloud SQL : facturé même instance arrêtée.',
    issue: 63,
  },
  {
    id: 'meme-origine',
    title: 'Même origine pour le front et les API, sans CORS',
    date: '2026-11-13',
    context: 'Le front Angular appelle `shipment-service` et `ingestion-service`.',
    choice:
      'Le navigateur ne parle qu’à l’origine du front : `ng serve` avec un proxy en local, un service nginx sur Cloud Run qui route `/api/scans` vers `ingestion-service` et le reste vers `shipment-service`. Le front n’emploie que des chemins relatifs, jamais d’URL de service.',
    rejected: 'Des URL absolues par service et une configuration CORS à maintenir et à sécuriser.',
    issue: 81,
  },
  {
    id: 'jeton-local-storage',
    title: 'Jeton dans localStorage : risque accepté, borné par la CSP',
    date: '2026-11-13',
    context:
      'Le jeton doit survivre à la fermeture de l’onglet. Un script injecté pourrait le lire dans `localStorage`.',
    choice:
      'Jeton dans `localStorage`, valable 12 h au plus (SCANNER), avec déconnexion synchronisée entre onglets. Le risque est écrit et borné : CSP `script-src \'self\'` sans `unsafe-inline` ni `unsafe-eval`, `connect-src \'self\'`, aucun script tiers.',
    rejected: 'Un cookie `HttpOnly` et son cycle de session côté serveur, plus lourd pour ce périmètre.',
    issue: 81,
  },
];
