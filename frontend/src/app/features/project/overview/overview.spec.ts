import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Overview } from './overview';
import { parseProjectStats } from './project-stats';

async function render() {
  await TestBed.configureTestingModule({
    imports: [Overview],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  }).compileComponents();
  const fixture = TestBed.createComponent(Overview);
  const http = TestBed.inject(HttpTestingController);
  return { fixture, http, el: fixture.nativeElement as HTMLElement };
}

describe('Overview (chiffres du projet)', () => {
  it('affiche les chiffres, la date et le commit', async () => {
    const { fixture, http, el } = await render();
    http
      .expectOne('/project-stats.json')
      .flush({
        services: 4,
        javaLines: 12345,
        generatedAt: '2026-11-14T10:00:00Z',
        commit: 'abc1234',
      });
    await fixture.whenStable();
    const block = el.querySelector('[data-testid="project-stats"]');
    expect(block?.textContent).toContain('Services');
    expect(block?.textContent).toContain('Lignes de Java');
    expect(block?.querySelector('footer')?.textContent).toContain('abc1234');
    expect(block?.querySelector('footer')?.textContent).toContain('14 novembre 2026');
  });

  it('masque le bloc sur une erreur 404', async () => {
    const { fixture, http, el } = await render();
    http.expectOne('/project-stats.json').flush('', { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="project-stats"]')).toBeNull();
  });

  it('masque le bloc sur un contenu illisible (page HTML)', async () => {
    const { fixture, http, el } = await render();
    http.expectOne('/project-stats.json').flush('<!doctype html>');
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="project-stats"]')).toBeNull();
  });
});

describe('parseProjectStats', () => {
  it('ignore les champs invalides et rend null sans aucun chiffre', () => {
    expect(parseProjectStats({ services: 'beaucoup', commit: 'x' })).toBeNull();
    expect(parseProjectStats(null)).toBeNull();
    expect(parseProjectStats([1])).toBeNull();
    const parsed = parseProjectStats({ services: 4, commits: -1, commit: '<script>' });
    expect(parsed?.figures).toEqual([{ label: 'Services', value: 4 }]);
    expect(parsed?.commit).toBeUndefined();
  });
});
