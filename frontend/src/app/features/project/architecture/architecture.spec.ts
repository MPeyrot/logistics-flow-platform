import { TestBed } from '@angular/core/testing';
import { Architecture } from './architecture';
import { BLOCKS, EDGES, SCAN_STEPS } from './architecture-data';

async function render() {
  await TestBed.configureTestingModule({ imports: [Architecture] }).compileComponents();
  const fixture = TestBed.createComponent(Architecture);
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const activeEdges = (el: HTMLElement) =>
  Array.from(el.querySelectorAll('[data-edge].active')).map((e) => e.getAttribute('data-edge'));

describe('Architecture (données)', () => {
  it('les étapes et les flèches ne citent que des blocs et flèches qui existent', () => {
    const blocks = BLOCKS.map((b) => b.id);
    const edges = EDGES.map((e) => e.id);
    for (const e of EDGES) {
      expect(blocks).toContain(e.from);
      expect(blocks).toContain(e.to);
    }
    for (const s of SCAN_STEPS) {
      s.edges.forEach((id) => expect(edges).toContain(id));
      s.blocks.forEach((id) => expect(blocks).toContain(id));
    }
  });

  it('aucun bloc ne parle d’expérience en production', () => {
    const text = JSON.stringify(BLOCKS).toLowerCase();
    expect(text).not.toContain('en production');
  });
});

describe('Architecture (page)', () => {
  it('dessine les huit blocs et liste les mêmes sous le schéma', async () => {
    const { el } = await render();
    expect(el.querySelectorAll('svg [data-block]').length).toBe(8);
    expect(el.querySelectorAll('[data-testid="block-list"] li').length).toBe(8);
    for (const kind of ['rest', 'kafka', 'jwt']) {
      expect(el.querySelector(`[data-edge].${kind}`), kind).not.toBeNull();
    }
  });

  it('un clic sur un bloc ouvre son panneau (rôle, technologies, validations, limites)', async () => {
    const { fixture, el } = await render();
    el.querySelector('svg [data-block="ingestion"]')!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
    const panel = el.querySelector('[data-testid="block-details"]') as HTMLElement;
    expect(panel.textContent).toContain('ingestion-service');
    for (const h of ['Rôle', 'Technologies', 'Ce qu’il valide', 'Limites']) {
      expect(panel.textContent).toContain(h);
    }
    expect(el.querySelector('svg [data-block="ingestion"]')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('le clavier (Entrée) choisit aussi un bloc', async () => {
    const { fixture, el } = await render();
    const kafka = el.querySelector('svg [data-block="kafka"]') as HTMLElement;
    kafka.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="block-details"]')?.textContent).toContain('scan-events.DLT');
  });

  it('« Suivre un scan » met en évidence les flèches de chaque étape', async () => {
    const { fixture, el } = await render();
    expect(activeEdges(el)).toEqual([]);
    const next = () => {
      (el.querySelector('[data-testid="step-next"]') as HTMLElement).click();
      fixture.detectChanges();
    };
    next();
    expect(activeEdges(el)).toEqual(SCAN_STEPS[0].edges);
    expect(el.querySelector('[data-testid="step-details"]')?.textContent).toContain(SCAN_STEPS[0].title);
    next();
    expect(activeEdges(el)).toEqual(SCAN_STEPS[1].edges);
    for (let i = 0; i < 10; i++) next();
    expect(activeEdges(el)).toEqual(SCAN_STEPS[SCAN_STEPS.length - 1].edges);
  });

  it('« Arrêter » retire la mise en évidence', async () => {
    const { fixture, el } = await render();
    (el.querySelector('[data-testid="step-next"]') as HTMLElement).click();
    fixture.detectChanges();
    (el.querySelector('[data-testid="step-stop"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(activeEdges(el)).toEqual([]);
  });
});
