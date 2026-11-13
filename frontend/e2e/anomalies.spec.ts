import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  const payload = Buffer.from(JSON.stringify({ sub: 'olivia', roles: ['OPERATOR'] })).toString('base64url');
  const expiresAt = new Date(Date.now() + 3_600_000).toISOString();
  await page.addInitScript(
    ([token, exp]) => {
      localStorage.setItem('auth.token', token);
      localStorage.setItem('auth.expiresAt', exp);
    },
    [`e30.${payload}.sig`, expiresAt],
  );
}

test('onglets Ouvertes et Closes', async ({ page }) => {
  await signIn(page);
  await page.route('**/api/anomalies?open=*', (route) => {
    const open = new URL(route.request().url()).searchParams.get('open') === 'true';
    return route.fulfill({
      json: open
        ? [{ id: 'a1', shipmentReference: 'ABC-1', type: 'LATE', detectedAt: '2026-11-13T10:00:00Z', resolvedAt: null }]
        : [
            {
              id: 'a2',
              shipmentReference: 'XYZ-2',
              type: 'STALLED',
              detectedAt: '2026-11-05T10:00:00Z',
              resolvedAt: '2026-11-06T10:00:00Z',
            },
          ],
    });
  });

  await page.goto('/anomalies');
  await expect(page.getByRole('cell', { name: 'En retard' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'ABC-1' })).toHaveAttribute('href', '/shipments/ABC-1');
  await expect(page.getByRole('columnheader', { name: 'Close le' })).toHaveCount(0);

  await page.getByRole('tab', { name: 'Closes' }).click();
  await expect(page.getByRole('cell', { name: 'Sans mouvement' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Close le' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'XYZ-2' })).toBeVisible();

  await page.getByRole('button', { name: 'Actualiser' }).click();
  await expect(page.getByRole('cell', { name: 'Sans mouvement' })).toBeVisible();
});
