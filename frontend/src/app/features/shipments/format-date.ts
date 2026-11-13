const format = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' });

/** Date et heure en `fr-FR`, fuseau du navigateur ; tiret pour une date absente. */
export function formatDateTime(value: string | null | undefined): string {
  return value ? format.format(new Date(value)) : '—';
}
