import { expect, test, type Page } from '@playwright/test';
import { mockApi, mockProblem } from './mock-api';

async function signIn(page: Page) {
  const payload = Buffer.from(JSON.stringify({ sub: 'olivia', roles: ['OPERATOR'] })).toString(
    'base64url',
  );
  const expiresAt = new Date(Date.now() + 3_600_000).toISOString();
  await page.addInitScript(
    ([token, exp]) => {
      localStorage.setItem('auth.token', token);
      localStorage.setItem('auth.expiresAt', exp);
    },
    [`e30.${payload}.sig`, expiresAt],
  );
}

const detail = (status: string, extra: Record<string, unknown> = {}) => ({
  id: 'x1',
  reference: 'ABC-1',
  origin: 'Paris',
  destination: 'Lyon',
  status,
  createdAt: '2026-11-01T10:00:00Z',
  expectedDeliveryAt: '2026-11-20T10:00:00Z',
  openAnomalies: [],
  scans: [],
  anomalies: [],
  ...extra,
});

const NOTE = 'Saisie manuelle par le bureau, ouverte aux opérateurs pour les besoins de la démo.';

test('scan réussi : le statut se rafraîchit, mention affichée', async ({ page }) => {
  await signIn(page);
  let loads = 0;
  await page.route('**/api/shipments/ABC-1', (route) => {
    loads++;
    const body = loads < 3 ? detail('CREATED') : detail('IN_TRANSIT');
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
  let sent: Record<string, unknown> | null = null;
  await page.route('**/api/scans', (route) => {
    sent = route.request().postDataJSON();
    return route.fulfill({ status: 202, contentType: 'application/json', body: '{"eventId":"e"}' });
  });
  await page.goto('/shipments/ABC-1');

  await expect(page.getByText(NOTE)).toBeVisible();
  await expect(page.getByLabel('Site du scan')).toHaveValue('Paris');
  await page.getByRole('button', { name: 'Prise en charge' }).click();

  await expect(page.locator('.badge')).toHaveText('En transit');
  expect(sent).toMatchObject({ shipmentReference: 'ABC-1', type: 'PICKED_UP', site: 'Paris' });
  expect(sent).not.toHaveProperty('occurredAt');
  await expect(page.getByRole('button', { name: 'En livraison' })).toBeVisible();
});

test('400 : le détail du problème sous les boutons', async ({ page }) => {
  await signIn(page);
  await mockApi(page, '**/api/shipments/ABC-1', { body: detail('CREATED') });
  await mockProblem(page, '**/api/scans', 400, 'site: ne doit pas être vide');
  await page.goto('/shipments/ABC-1');
  await page.getByRole('button', { name: 'Prise en charge' }).click();
  await expect(
    page.getByRole('alert').filter({ hasText: 'site: ne doit pas être vide' }),
  ).toBeVisible();
});

test('403 : accès refusé sous les boutons', async ({ page }) => {
  await signIn(page);
  await mockApi(page, '**/api/shipments/ABC-1', { body: detail('IN_TRANSIT') });
  await mockProblem(page, '**/api/scans', 403, 'forbidden');
  await page.goto('/shipments/ABC-1');
  await page.getByRole('button', { name: 'En livraison' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Accès refusé' })).toBeVisible();
});

test('livrée : aucun bouton ; la frise mêle scans et anomalies', async ({ page }) => {
  await signIn(page);
  await mockApi(page, '**/api/shipments/ABC-1', {
    body: detail('DELIVERED', {
      scans: [
        {
          eventId: 's1',
          type: 'PICKED_UP',
          site: 'Paris',
          occurredAt: '2026-11-02T08:00:00Z',
          receivedAt: '2026-11-02T08:00:01Z',
        },
        {
          eventId: 's2',
          type: 'DELIVERED',
          site: 'Lyon',
          occurredAt: '2026-11-09T08:00:00Z',
          receivedAt: '2026-11-09T08:00:01Z',
        },
      ],
      anomalies: [
        {
          id: 'a1',
          type: 'STALLED',
          detectedAt: '2026-11-06T08:00:00Z',
          resolvedAt: '2026-11-09T08:00:02Z',
        },
      ],
    }),
  });
  await page.goto('/shipments/ABC-1');
  await expect(page.getByRole('button', { name: 'Livrée' })).toHaveCount(0);
  await expect(page.getByText(NOTE)).toHaveCount(0);
  const items = page
    .getByRole('list', { name: 'Frise des scans et des anomalies' })
    .getByRole('listitem');
  await expect(items).toHaveCount(4);
  await expect(items.nth(1)).toContainText('anomalie ouverte');
  await expect(items.nth(3)).toContainText('anomalie close');
});
