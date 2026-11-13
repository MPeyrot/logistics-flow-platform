export const MAX_CSV_BYTES = 2 * 1024 * 1024;

export const TEMPLATE_CSV = 'shipmentReference,type,site,occurredAt,eventId,reason\n';

export interface ImportError {
  line: number;
  detail: string;
}

export interface ImportReport {
  lines: number;
  published: number;
  rejected: number;
  errors: ImportError[];
}

/** Message d'erreur si le fichier est refusé avant l'envoi, sinon null. */
export function checkCsv(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.csv')) {
    return 'Format non accepté : un fichier .csv est attendu.';
  }
  if (file.size > MAX_CSV_BYTES) {
    return 'Fichier trop volumineux : 2 Mo au plus.';
  }
  return null;
}

function cell(value: string): string {
  // Une cellule qui commence comme une formule serait exécutée par un tableur.
  const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
  return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}

/** CSV des erreurs du rapport (une ligne par erreur). */
export function errorsCsv(errors: ImportError[]): string {
  return ['line,detail', ...errors.map((e) => `${e.line},${cell(e.detail)}`)].join('\r\n') + '\r\n';
}
