export type BlockId =
  | 'browser'
  | 'nginx'
  | 'ingestion'
  | 'kafka'
  | 'shipment'
  | 'db'
  | 'ai'
  | 'claude';

export type EdgeKind = 'rest' | 'kafka' | 'jwt';

export interface Block {
  id: BlockId;
  /** Nom court dans le schéma. */
  label: string;
  /** Deuxième ligne du schéma (technologie ou rôle en trois mots). */
  caption: string;
  x: number;
  y: number;
  width: number;
  height: number;
  role: string;
  technologies: string[];
  validates: string[];
  limits: string[];
}

export interface Edge {
  id: string;
  from: BlockId;
  to: BlockId;
  kind: EdgeKind;
  label: string;
  path: string;
  labelX: number;
  labelY: number;
  anchor: 'start' | 'middle' | 'end';
}

export interface ScanStep {
  title: string;
  text: string;
  blocks: BlockId[];
  edges: string[];
}

/** Schéma dans un `viewBox` 640 x 340. Faits : `.claude/rules/architecture.md`. */
export const BLOCKS: Block[] = [
  {
    id: 'browser',
    label: 'Navigateur',
    caption: 'Angular',
    x: 10,
    y: 140,
    width: 110,
    height: 56,
    role: 'Le front Angular s’exécute ici. Il ne parle qu’à l’origine du front, avec des chemins relatifs et le même JWT.',
    technologies: ['Angular (TypeScript), sans rendu serveur', 'Jeton dans localStorage, claims lus pour l’affichage seulement'],
    validates: ['Les formulaires reprennent les règles du serveur (référence, motif, mot de passe), pour guider la saisie.'],
    limits: [
      'Un script injecté lirait le jeton (12 h au plus pour un SCANNER) : risque accepté, borné par la CSP.',
      'Aucun CORS, aucune URL absolue de service.',
    ],
  },
  {
    id: 'nginx',
    label: 'nginx (front)',
    caption: 'Cloud Run',
    x: 150,
    y: 140,
    width: 110,
    height: 56,
    role: 'Sert le front et route l’API : /api/scans vers ingestion-service, le reste de /api/ et /.well-known/ vers shipment-service.',
    technologies: ['nginx non root (image par Dockerfile)', 'Service Cloud Run « frontend »'],
    validates: ['Pose la CSP : script-src et connect-src limités à l’origine, pas de script tiers.'],
    limits: ['Aucun secret, 256 Mio, 0 à 1 instance.', 'Ne vérifie pas les jetons : les services le font.'],
  },
  {
    id: 'ingestion',
    label: 'ingestion-service',
    caption: 'scans, import CSV',
    x: 290,
    y: 20,
    width: 150,
    height: 56,
    role: 'Point d’entrée des scans (saisie unitaire et import CSV synchrone) : valide puis publie sur Kafka. Sans base de données.',
    technologies: ['Spring Boot (Java 21), architecture hexagonale', 'Spring Security (resource server), Spring Batch pour l’import', 'Producteur Kafka'],
    validates: [
      'Jeton JWT (JWKS de shipment-service), rôle SCANNER, site du scan = claim « site ».',
      'Corps du scan par Bean Validation ; motif (reason) seulement avec DELIVERY_FAILED, 200 caractères au plus.',
      'Import : 2 Mo et 10 000 lignes au plus, lignes invalides sautées et rapportées.',
    ],
    limits: [
      'Un seul import à la fois par instance ; le second attend 30 s, puis 429.',
      'Kafka en panne : 503 en ~10 s au plus.',
    ],
  },
  {
    id: 'kafka',
    label: 'Kafka',
    caption: 'scan-events · .DLT',
    x: 290,
    y: 140,
    width: 150,
    height: 56,
    role: 'Transporte les scans : topic scan-events (3 partitions, clé = référence d’expédition) et scan-events.DLT pour les messages en échec.',
    technologies: ['Confluent Cloud en ligne, Kafka en Docker Compose en local', 'Messages JSON, schéma versionné (scan-event.v2)'],
    validates: ['La clé = référence d’expédition garantit l’ordre des scans d’une même expédition.'],
    limits: [
      'Aucune bibliothèque partagée : le contrat JSON dans contracts/ fait foi.',
      'Une « expédition inconnue » bloque sa partition jusqu’à ~5 min (backoff), pour garder l’ordre.',
    ],
  },
  {
    id: 'shipment',
    label: 'shipment-service',
    caption: 'expéditions, jetons',
    x: 290,
    y: 260,
    width: 150,
    height: 56,
    role: 'Expéditions, comptes, statuts, anomalies. Émet les JWT (RS256), consomme les scans, appelle ai-service.',
    technologies: ['Spring Boot (Java 21), hexagonale', 'Hibernate, Flyway', 'Spring Security, consommateur Kafka'],
    validates: [
      'Ses propres jetons (iss, exp, aud) ; la matrice d’accès donne 401 sans jeton, 403 si le rôle ne convient pas.',
      'Chaque message Kafka par Bean Validation, puis la transition de statut (six permises, les autres vont au DLT).',
      'Idempotence : clé primaire scan_event.event_id.',
    ],
    limits: [
      'min et max 1 instance sur Cloud Run : détection des anomalies et limites de débit en mémoire.',
      '20 extractions par heure et par utilisateur, 5 échecs de connexion avant blocage de 15 min.',
    ],
  },
  {
    id: 'db',
    label: 'PostgreSQL',
    caption: 'Neon',
    x: 490,
    y: 260,
    width: 140,
    height: 56,
    role: 'Base de shipment-service seulement : expéditions, scans, anomalies, comptes, rejeux du DLT.',
    technologies: ['PostgreSQL 17 sur Neon (offre gratuite), connexion TLS', 'Migrations Flyway uniquement'],
    validates: ['Contraintes en base : une seule anomalie ouverte par expédition et type.'],
    limits: ['Les services ne partagent pas de base : ils échangent par REST ou Kafka.'],
  },
  {
    id: 'ai',
    label: 'ai-service',
    caption: 'lecture des bons',
    x: 490,
    y: 140,
    width: 140,
    height: 56,
    role: 'Extrait les champs d’un bon de livraison (PDF ou image) en appelant l’API Claude. Sans base de données.',
    technologies: ['Spring Boot (Java 21), hexagonale', 'SDK Java officiel d’Anthropic, utilisé dans infrastructure/claude seulement', 'Claude Haiku 5.5 par défaut'],
    validates: [
      'Jeton : sub = shipment-service, aud = ai-service.',
      'Type du fichier reconnu par ses premiers octets ; réponse du modèle contrôlée par Bean Validation (422 sinon).',
    ],
    limits: [
      'Budget quotidien de 1 $ (configurable) et 100 appels par heure, en mémoire ; max 1 instance.',
      'Les tests simulent l’API avec WireMock : aucun appel réel.',
    ],
  },
  {
    id: 'claude',
    label: 'API Claude',
    caption: 'Anthropic',
    x: 490,
    y: 20,
    width: 140,
    height: 56,
    role: 'Modèle de langage externe qui lit le bon de livraison et rend un JSON.',
    technologies: ['API HTTPS d’Anthropic'],
    validates: ['Sa réponse est une donnée externe : jamais crue sans contrôle.'],
    limits: ['Chaque appel coûte (~0,0004 $ avec Haiku 5.5) : coût journalisé et plafonné.'],
  },
];

export const EDGES: Edge[] = [
  { id: 'browser-nginx', from: 'browser', to: 'nginx', kind: 'rest', label: 'HTTPS', path: 'M120 168 H148', labelX: 134, labelY: 130, anchor: 'middle' },
  { id: 'nginx-ingestion', from: 'nginx', to: 'ingestion', kind: 'rest', label: '/api/scans', path: 'M240 140 L290 56', labelX: 200, labelY: 80, anchor: 'start' },
  { id: 'nginx-shipment', from: 'nginx', to: 'shipment', kind: 'rest', label: '/api/…', path: 'M240 196 L290 280', labelX: 200, labelY: 250, anchor: 'start' },
  { id: 'ingestion-kafka', from: 'ingestion', to: 'kafka', kind: 'kafka', label: 'scan-events', path: 'M340 76 V138', labelX: 346, labelY: 112, anchor: 'start' },
  { id: 'kafka-shipment', from: 'kafka', to: 'shipment', kind: 'kafka', label: 'scan-events', path: 'M340 196 V258', labelX: 334, labelY: 232, anchor: 'end' },
  { id: 'shipment-dlt', from: 'shipment', to: 'kafka', kind: 'kafka', label: '.DLT', path: 'M400 258 V198', labelX: 406, labelY: 232, anchor: 'start' },
  { id: 'shipment-db', from: 'shipment', to: 'db', kind: 'rest', label: 'JDBC', path: 'M440 288 H488', labelX: 464, labelY: 306, anchor: 'middle' },
  { id: 'shipment-ai', from: 'shipment', to: 'ai', kind: 'jwt', label: 'REST + JWT', path: 'M440 266 L520 198', labelX: 540, labelY: 232, anchor: 'start' },
  { id: 'ai-claude', from: 'ai', to: 'claude', kind: 'rest', label: 'HTTPS', path: 'M560 140 V78', labelX: 566, labelY: 112, anchor: 'start' },
  { id: 'ingestion-jwks', from: 'ingestion', to: 'shipment', kind: 'jwt', label: 'JWKS', path: 'M440 48 C 468 48, 468 126, 468 150 V 232 C 468 250, 458 262, 440 270', labelX: 474, labelY: 100, anchor: 'start' },
];

export const SCAN_STEPS: ScanStep[] = [
  {
    title: 'Saisie',
    text: 'Le SCANNER saisit un scan (ou importe un CSV) dans le navigateur. Le front l’envoie à /api/scans avec son jeton.',
    blocks: ['browser', 'nginx'],
    edges: ['browser-nginx'],
  },
  {
    title: 'Ingestion',
    text: 'nginx renvoie vers ingestion-service, qui contrôle le jeton (clé publique de shipment-service), le rôle et le site, puis valide le corps du scan.',
    blocks: ['nginx', 'ingestion'],
    edges: ['nginx-ingestion', 'ingestion-jwks'],
  },
  {
    title: 'Kafka',
    text: 'Le scan valide est publié sur scan-events, avec la référence d’expédition pour clé : l’ordre par expédition est garanti.',
    blocks: ['ingestion', 'kafka'],
    edges: ['ingestion-kafka'],
  },
  {
    title: 'Expédition',
    text: 'shipment-service consomme le message (idempotent), applique la transition de statut et referme ou ouvre les anomalies. Un message illisible ou une transition interdite part dans scan-events.DLT.',
    blocks: ['kafka', 'shipment', 'db'],
    edges: ['kafka-shipment', 'shipment-dlt', 'shipment-db'],
  },
  {
    title: 'Affichage',
    text: 'Le détail de l’expédition est lu par le front via /api/shipments : statut, scans et anomalies à jour.',
    blocks: ['browser', 'nginx', 'shipment'],
    edges: ['browser-nginx', 'nginx-shipment'],
  },
];
