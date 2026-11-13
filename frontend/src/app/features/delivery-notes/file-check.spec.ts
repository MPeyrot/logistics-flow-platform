import { checkFile, MAX_FILE_BYTES } from './file-check';

const file = (name: string, size = 100) => ({ name, size }) as File;

describe('checkFile', () => {
  it.each(['bon.pdf', 'bon.PNG', 'bon.jpg', 'bon.jpeg', 'bon.gif', 'scan.final.webp'])(
    'accepte %s',
    (name) => {
      expect(checkFile(file(name))).toBeNull();
    },
  );

  it('refuse une autre extension', () => {
    expect(checkFile(file('bon.docx'))).toBe('Format non accepté : PDF, PNG, JPEG, GIF ou WebP.');
  });

  it('refuse un fichier sans extension', () => {
    expect(checkFile(file('bon'))).toContain('Format non accepté');
  });

  it('garde une marge de 16 Kio sous le plafond de 10 Mo de la requête multipart', () => {
    expect(MAX_FILE_BYTES).toBe(10 * 1024 * 1024 - 16 * 1024);
    expect(checkFile(file('bon.pdf', MAX_FILE_BYTES))).toBeNull();
    expect(checkFile(file('bon.pdf', MAX_FILE_BYTES + 1))).toBe('Fichier trop volumineux : 10 Mo au plus.');
  });
});
