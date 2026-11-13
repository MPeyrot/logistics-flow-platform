import { describe, expect, it } from 'vitest';
import { checkCsv, errorsCsv, TEMPLATE_CSV } from './import-report';

describe('rapport d’import', () => {
  it('produit un CSV des erreurs avec en-tête et guillemets échappés', () => {
    expect(
      errorsCsv([
        { line: 3, detail: 'type: must not be null' },
        { line: 7, detail: 'dit "non", puis, une virgule' },
      ]),
    ).toBe('line,detail\r\n3,type: must not be null\r\n7,"dit ""non"", puis, une virgule"\r\n');
  });

  it('neutralise les cellules qui commencent comme une formule', () => {
    expect(errorsCsv([{ line: 2, detail: '=1+1' }])).toBe("line,detail\r\n2,'=1+1\r\n");
  });

  it('a le modèle attendu', () => {
    expect(TEMPLATE_CSV).toBe('shipmentReference,type,site,occurredAt,eventId,reason\n');
  });

  it('contrôle l’extension et la taille', () => {
    expect(checkCsv(new File(['a'], 'scans.csv'))).toBeNull();
    expect(checkCsv(new File(['a'], 'SCANS.CSV'))).toBeNull();
    expect(checkCsv(new File(['a'], 'scans.txt'))).toMatch(/\.csv/);
    expect(checkCsv(new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'a.csv'))).toMatch(/2 Mo/);
    expect(checkCsv(new File([new Uint8Array(2 * 1024 * 1024)], 'a.csv'))).toBeNull();
  });
});
