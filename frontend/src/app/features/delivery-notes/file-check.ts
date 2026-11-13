export const MAX_FILE_BYTES = 10 * 1024 * 1024;

const EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg', 'gif', 'webp'];

/** Message d'erreur si le fichier est refusé avant l'envoi, sinon null. */
export function checkFile(file: File): string | null {
  const dot = file.name.lastIndexOf('.');
  const extension = dot < 0 ? '' : file.name.slice(dot + 1).toLowerCase();
  if (!EXTENSIONS.includes(extension)) {
    return 'Format non accepté : PDF, PNG, JPEG, GIF ou WebP.';
  }
  if (file.size > MAX_FILE_BYTES) {
    return 'Fichier trop volumineux : 10 Mo au plus.';
  }
  return null;
}
