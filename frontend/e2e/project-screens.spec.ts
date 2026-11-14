import { expect, test } from '@playwright/test';

test.describe('/projet/ecrans', () => {
  test('montre les huit captures chargées, avec légende et texte alternatif', async ({ page }) => {
    await page.goto('/projet/ecrans');
    const figures = page.getByRole('figure');
    await expect(figures).toHaveCount(8);
    for (const figure of await figures.all()) {
      await expect(figure.getByRole('img')).toHaveAttribute('alt', /.{20}/);
      await expect(figure.locator('figcaption')).not.toBeEmpty();
      await figure.scrollIntoViewIfNeeded();
      await expect
        .poll(() => figure.getByRole('img').evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0))
        .toBe(true);
    }
  });

  test('le thème sombre choisit la capture sombre', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/projet/ecrans');
    const first = page.getByRole('figure').first().getByRole('img');
    await expect.poll(() => first.evaluate((img: HTMLImageElement) => img.currentSrc)).toContain('-dark.jpg');
    await page.emulateMedia({ colorScheme: 'light' });
    await expect.poll(() => first.evaluate((img: HTMLImageElement) => img.currentSrc)).toContain('-light.jpg');
  });

  test('propose de se connecter pour essayer, sans défilement horizontal', async ({ page }) => {
    await page.goto('/projet/ecrans');
    await expect(page.getByRole('link', { name: 'Se connecter pour essayer' }).first()).toHaveAttribute('href', '/login');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
});
