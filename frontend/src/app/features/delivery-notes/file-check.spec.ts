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

  it('accepte exactement 10 Mo et refuse au-delà', () => {
    expect(MAX_FILE_BYTES).toBe(10 * 1024 * 1024);
    expect(checkFile(file('bon.pdf', 10 * 1024 * 1024))).toBeNull();
    expect(checkFile(file('bon.pdf', 10 * 1024 * 1024 + 1))).toBe('Fichier trop volumineux : 10 Mo au plus.');
  });
});
