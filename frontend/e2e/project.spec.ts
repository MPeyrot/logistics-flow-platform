import { expect, test } from '@playwright/test';

const NOTICE = 'Projet personnel, conçu et développé seul pour démontrer. Ce n’est pas un logiciel en production.';

test.describe('visiteur sans session', () => {
  test('/ mène à /projet, avec la mention et sans aucun appel à l’API', async ({ page }) => {
    const apiCalls: string[] = [];
    page.on('request', (r) => {
      if (r.url().includes('/api/')) {
        apiCalls.push(r.url());
      }
    });
    await page.goto('/');
    await expect(page).toHaveURL(/\/projet$/);
    await expect(page.getByTestId('project-notice')).toHaveText(NOTICE);
    await expect(page.getByRole('link', { name: 'Se connecter' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Retour à l’application' })).toHaveCount(0);
    expect(apiCalls).toEqual([]);
  });

  test('navigue entre les cinq pages, la mention reste en tête', async ({ page }) => {
    await page.goto('/projet');
    const nav = page.getByRole('navigation', { name: 'Pages du projet' });
    for (const [name, url] of [
      ['Architecture', /\/projet\/architecture$/],
      ['Statuts', /\/projet\/statuts$/],
      ['Décisions', /\/projet\/decisions$/],
      ['Écrans', /\/projet\/ecrans$/],
      ['Vue d’ensemble', /\/projet$/],
    ] as const) {
      await nav.getByRole('link', { name }).click();
      await expect(page).toHaveURL(url);
      await expect(page.getByTestId('project-notice')).toHaveText(NOTICE);
    }
  });

  test('une URL protégée mène toujours à /login, et la connexion propose le projet', async ({ page }) => {
    await page.goto('/shipments');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fshipments/);
    await page.getByRole('link', { name: 'Découvrir le projet' }).click();
    await expect(page).toHaveURL(/\/projet$/);
  });

  test('diagramme : un statut choisi au clavier montre ses transitions et un exemple refusé', async ({ page }) => {
    await page.goto('/projet/statuts');
    await expect(page.getByTestId('all-transitions').getByRole('listitem')).toHaveCount(6);
    await expect(page.getByTestId('status-details')).toContainText('Aucun statut choisi');

    const inTransit = page.getByRole('button', { name: 'Statut En transit' });
    await inTransit.focus();
    await page.keyboard.press('Enter');
    const details = page.getByTestId('status-details');
    await expect(details).toContainText('Arrivée sur un site');
    await expect(details).toContainText('Départ en livraison');
    await expect(page.getByTestId('refused-example')).toContainText('Un scan « Livré » sur une expédition « En transit » est refusé');
    await expect(page.getByTestId('refused-example')).toContainText('DLT');

    await page.getByRole('button', { name: 'Statut Livrée' }).click();
    await expect(details).toContainText('terminal');
    await expect(page.getByText('Sans mouvement')).toBeVisible();
    await expect(page.getByText('En retard (')).toBeVisible();
  });

  test('pas de défilement horizontal', async ({ page }) => {
    await page.goto('/projet/statuts');
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test('vue d’ensemble : le bloc des chiffres s’affiche avec un JSON simulé', async ({ page }) => {
    await page.route('**/project-stats.json', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          services: 4,
          javaLines: 12345,
          generatedAt: '2026-11-14T10:00:00Z',
          commit: 'abc1234',
        }),
      }),
    );
    await page.goto('/projet');
    const block = page.getByTestId('project-stats');
    await expect(block).toContainText('Lignes de Java');
    await expect(block).toContainText('abc1234');
    await expect(block).toContainText('14 novembre 2026');
  });

  test('vue d’ensemble : le bloc des chiffres est masqué sur 404', async ({ page }) => {
    await page.route('**/project-stats.json', (route) =>
      route.fulfill({ status: 404, body: 'Not found' }),
    );
    await page.goto('/projet');
    await expect(
      page.getByRole('heading', { name: 'Suivre un colis de bout en bout' }),
    ).toBeVisible();
    await expect(page.getByTestId('project-stats')).toHaveCount(0);
  });
});

test.describe('avec une session', () => {
  test.beforeEach(async ({ page }) => {
    const payload = Buffer.from(JSON.stringify({ sub: 'alice', roles: ['OPERATOR'] })).toString('base64url');
    const expiresAt = new Date(Date.now() + 3_600_000).toISOString();
    await page.addInitScript(
      ([token, exp]) => {
        localStorage.setItem('auth.token', token);
        localStorage.setItem('auth.expiresAt', exp);
      },
      [`e30.${payload}.sig`, expiresAt],
    );
    await page.route('**/api/**', (route) => route.fulfill({ json: [] }));
  });

  test('« Retour à l’application » ramène au tableau de bord ; « Le projet » est dans la barre latérale', async ({
    page,
  }) => {
    await page.goto('/projet/statuts');
    await expect(page.getByRole('link', { name: 'Se connecter' })).toHaveCount(0);
    await page.getByRole('link', { name: 'Retour à l’application' }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await page.getByRole('link', { name: 'Le projet' }).click();
    await expect(page).toHaveURL(/\/projet$/);
  });
});
