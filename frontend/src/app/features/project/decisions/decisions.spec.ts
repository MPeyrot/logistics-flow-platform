import { TestBed } from '@angular/core/testing';
import { Decisions } from './decisions';
import { DECISIONS, JOURNAL_URL, issueUrl } from './decisions-data';

describe('Décisions (données)', () => {
  it('compte entre 12 et 15 décisions, toutes complètes et à identifiant unique', () => {
    expect(DECISIONS.length).toBeGreaterThanOrEqual(12);
    expect(DECISIONS.length).toBeLessThanOrEqual(15);
    expect(new Set(DECISIONS.map((d) => d.id)).size).toBe(DECISIONS.length);
    for (const d of DECISIONS) {
      for (const field of [d.title, d.context, d.choice, d.rejected]) {
        expect(field.trim().length, d.id).toBeGreaterThan(20);
      }
      expect(d.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(Number.isInteger(d.issue) && d.issue > 0, d.id).toBe(true);
    }
  });

  it('couvre les sujets attendus', () => {
    const ids = DECISIONS.map((d) => d.id);
    for (const id of [
      'hexagonale',
      'kafka-ordre',
      'retries-bloquantes',
      'outbox-dlt',
      'stalled-reception',
      'budget-dollars',
      'modele-mesure',
      'neon',
      'meme-origine',
      'jeton-local-storage',
    ]) {
      expect(ids, id).toContain(id);
    }
  });

  it('aucune formulation d’expérience en production', () => {
    expect(JSON.stringify(DECISIONS).toLowerCase()).not.toContain('en production');
  });
});

describe('Décisions (page)', () => {
  it('liste chaque décision avec son lien vers l’issue, et le journal complet', async () => {
    await TestBed.configureTestingModule({ imports: [Decisions] }).compileComponents();
    const fixture = TestBed.createComponent(Decisions);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[data-decision]').length).toBe(DECISIONS.length);
    const first = el.querySelector('[data-decision="hexagonale"] a') as HTMLAnchorElement;
    expect(first.href).toBe(issueUrl(69));
    expect(first.href.startsWith('https://github.com/MPeyrot/logistics-flow-platform/issues/')).toBe(true);
    const journal = Array.from(el.querySelectorAll('a')).find((a) => a.textContent?.includes('Journal complet'));
    expect(journal?.getAttribute('href')).toBe(JOURNAL_URL);
    expect(el.querySelector('[data-decision="hexagonale"] code')?.textContent).toBe('domain');
  });
});
