import { expect, test } from '@playwright/test';

test.describe('/projet/architecture', () => {
  test('affiche le schéma, la liste des blocs et aucun appel à l’API', async ({ page }) => {
    const apiCalls: string[] = [];
    page.on('request', (r) => {
      if (r.url().includes('/api/')) {
        apiCalls.push(r.url());
      }
    });
    await page.goto('/projet/architecture');
    await expect(page.getByRole('heading', { name: 'L’architecture en un schéma' })).toBeVisible();
    await expect(page.locator('svg [data-block]')).toHaveCount(8);
    await expect(page.getByTestId('block-list').locator('li')).toHaveCount(8);
    expect(apiCalls).toEqual([]);
  });

  test('un clic sur un bloc ouvre son panneau, le clavier aussi', async ({ page }) => {
    await page.goto('/projet/architecture');
    const details = page.getByTestId('block-details');
    await expect(details).toContainText('Aucun bloc choisi');
    await page.getByRole('button', { name: 'Bloc ingestion-service' }).click();
    await expect(details).toContainText('Point d’entrée des scans');
    await expect(details).toContainText('Limites');
    await page.getByRole('button', { name: 'Bloc Kafka' }).focus();
    await page.keyboard.press('Enter');
    await expect(details).toContainText('scan-events.DLT');
  });

  test('« Suivre un scan » parcourt les cinq étapes et met les flèches en évidence', async ({ page }) => {
    await page.goto('/projet/architecture');
    const active = page.locator('[data-edge].active');
    await expect(active).toHaveCount(0);
    await page.getByRole('button', { name: 'Suivre un scan' }).click();
    await expect(page.getByTestId('step-details')).toContainText('Étape 1 sur 5 : Saisie');
    await expect(page.locator('[data-edge="browser-nginx"]')).toHaveClass(/active/);
    const next = page.getByRole('button', { name: 'Étape suivante' });
    await next.click();
    await next.click();
    await next.click();
    await expect(page.getByTestId('step-details')).toContainText('Étape 4 sur 5 : Expédition');
    await expect(page.locator('[data-edge="shipment-dlt"]')).toHaveClass(/active/);
    await next.click();
    await expect(next).toBeDisabled();
    await page.getByRole('button', { name: 'Arrêter' }).click();
    await expect(active).toHaveCount(0);
  });

  test('le mouvement des flèches s’arrête avec prefers-reduced-motion', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/projet/architecture');
    await page.getByRole('button', { name: 'Suivre un scan' }).click();
    const name = await page
      .locator('[data-edge="browser-nginx"] path')
      .evaluate((el) => getComputedStyle(el).animationName);
    expect(name).toBe('none');
  });

  test('tient dans la largeur, sans défilement horizontal', async ({ page }) => {
    await page.goto('/projet/architecture');
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test('thème sombre : le schéma reste lisible', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/projet/architecture');
    const fill = await page
      .locator('svg [data-block="kafka"] rect')
      .evaluate((el) => getComputedStyle(el).fill);
    expect(fill).not.toBe('none');
    await expect(page.locator('svg [data-block="kafka"] .name')).toBeVisible();
  });
});
