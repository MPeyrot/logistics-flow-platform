/** Un chiffre du bloc : son libellé et sa valeur. */
export interface StatFigure {
  label: string;
  value: number;
}

export interface ProjectStats {
  figures: StatFigure[];
  /** Date de génération en français (« 14 novembre 2026 »). */
  generatedOn?: string;
  commit?: string;
}

const LABELS: [key: string, label: string][] = [
  ['services', 'Services'],
  ['javaLines', 'Lignes de Java'],
  ['typescriptLines', 'Lignes de TypeScript'],
  ['javaTests', 'Tests Java'],
  ['vitestCases', 'Tests Vitest'],
  ['playwrightCases', 'Tests Playwright'],
  ['migrations', 'Migrations Flyway'],
  ['contracts', 'Contrats Kafka'],
  ['decisions', 'Décisions du journal'],
  ['mergedPullRequests', 'PR fusionnées'],
  ['commits', 'Commits'],
];

/** Lit `project-stats.json` (donnée externe) ; `null` si le contenu n'est pas un objet de chiffres exploitable. */
export function parseProjectStats(raw: unknown): ProjectStats | null {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    return null;
  }
  const data = raw as Record<string, unknown>;
  const figures = LABELS.flatMap(([key, label]) => {
    const value = data[key];
    return typeof value === 'number' && Number.isFinite(value) && value >= 0
      ? [{ label, value }]
      : [];
  });
  if (figures.length === 0) {
    return null;
  }
  const time = typeof data['generatedAt'] === 'string' ? Date.parse(data['generatedAt']) : NaN;
  const generatedOn = isNaN(time)
    ? undefined
    : new Date(time).toLocaleDateString('fr-FR', { dateStyle: 'long', timeZone: 'UTC' });
  const commit =
    typeof data['commit'] === 'string' && /^[0-9a-f]{4,40}$/.test(data['commit'])
      ? data['commit']
      : undefined;
  return { figures, generatedOn, commit };
}
