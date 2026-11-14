import { expect, test } from '@playwright/test';

test.describe('/projet/decisions', () => {
  test('liste les décisions, avec liens vers les issues et le journal, sans appel à l’API', async ({ page }) => {
    const apiCalls: string[] = [];
    page.on('request', (r) => {
      if (r.url().includes('/api/')) {
        apiCalls.push(r.url());
      }
    });
    await page.goto('/projet/decisions');
    await expect(page.getByRole('heading', { name: 'Les décisions qui ont façonné le projet' })).toBeVisible();
    const count = await page.getByTestId('decision-list').locator('> li').count();
    expect(count).toBeGreaterThanOrEqual(12);
    expect(count).toBeLessThanOrEqual(15);
    const issue = page.locator('[data-decision="outbox-dlt"]').getByRole('link', { name: /Issue #/ });
    await expect(issue).toHaveAttribute(
      'href',
      /^https:\/\/github\.com\/MPeyrot\/logistics-flow-platform\/issues\/\d+$/,
    );
    await expect(page.getByRole('link', { name: 'Journal complet' })).toHaveAttribute(
      'href',
      'https://github.com/MPeyrot/logistics-flow-platform/blob/main/.claude/rules/architecture.md',
    );
    expect(apiCalls).toEqual([]);
  });

  test('tient dans la largeur, sans défilement horizontal', async ({ page }) => {
    await page.goto('/projet/decisions');
    await expect(page.locator('[data-decision="neon"]')).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});
