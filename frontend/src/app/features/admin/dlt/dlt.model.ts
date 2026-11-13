/** `DeadLetterResponse` : message de `scan-events.DLT`, `value` brut (pas forcément du JSON). */
export interface DeadLetter {
  partition: number;
  offset: number;
  timestamp: string;
  key: string | null;
  value: string | null;
  exceptionClass: string | null;
  exceptionMessage: string | null;
  replayed: boolean;
  replayedAt: string | null;
}

/** `ReplayAllResponse` : avancement d'un « tout rejouer ». */
export interface ReplayProgress {
  id: string;
  finished: boolean;
  replayed: number;
  published: number;
  failed: number;
  error: string | null;
}

/** Nom court d'une classe Java : `a.b.MyException` devient `MyException`. */
export function shortClassName(name: string | null): string {
  if (!name) {
    return '—';
  }
  return name.slice(name.lastIndexOf('.') + 1);
}

/** JSON mis en forme ; le texte brut tel quel s'il n'est pas du JSON valide. */
export function prettyJson(value: string | null): string {
  if (value === null) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
