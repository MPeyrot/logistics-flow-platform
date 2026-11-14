import { expect, test, type Page } from '@playwright/test';
import { mockApi, mockProblem } from './mock-api';

/** Ouvre une session OPERATOR sans passer par l'écran de connexion. */
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

const shipment = (over: Record<string, unknown>) => ({
  id: String(over['reference']),
  origin: 'Paris',
  destination: 'Lyon',
  status: 'IN_TRANSIT',
  createdAt: '2026-11-01T10:00:00Z',
  expectedDeliveryAt: '2026-11-20T10:00:00Z',
  openAnomalies: [],
  ...over,
});

const LIST = [
  shipment({ reference: 'ABC-1' }),
  shipment({
    reference: 'XYZ-2',
    status: 'DELIVERED',
    openAnomalies: [{ type: 'LATE', detectedAt: '2026-11-13T10:00:00Z' }],
  }),
  shipment({ reference: 'ABD-3', status: 'CREATED' }),
];

test('liste filtrée puis détail', async ({ page }) => {
  await signIn(page);
  await mockApi(page, '**/api/shipments', { body: LIST }, 'GET');
  await mockApi(page, '**/api/shipments/XYZ-2', {
    body: {
      ...LIST[1],
      scans: [
        {
          eventId: 'e1',
          type: 'PICKED_UP',
          site: 'PAR-01',
          occurredAt: '2026-11-02T08:00:00Z',
          receivedAt: '2026-11-02T08:00:05Z',
        },
        {
          eventId: 'e2',
          type: 'DELIVERY_FAILED',
          site: 'LYO-02',
          occurredAt: '2026-11-03T08:00:00Z',
          receivedAt: '2026-11-03T08:00:05Z',
          reason: 'Destinataire absent',
        },
      ],
      anomalies: [
        { id: 'a1', type: 'LATE', detectedAt: '2026-11-13T10:00:00Z', resolvedAt: null },
        {
          id: 'a2',
          type: 'STALLED',
          detectedAt: '2026-11-05T10:00:00Z',
          resolvedAt: '2026-11-06T10:00:00Z',
        },
      ],
    },
  });

  await page.goto('/shipments');
  await expect(page.getByRole('row')).toHaveCount(4);

  await page.getByLabel('Rechercher une référence').fill('ab');
  await expect(page.getByRole('row')).toHaveCount(3);
  await page.getByLabel('Rechercher une référence').fill('');

  await page.getByLabel('Statut').selectOption('DELIVERED');
  await expect(page.getByRole('row')).toHaveCount(2);
  await page.getByLabel('Statut').selectOption('');

  await page.getByLabel('Avec anomalie ouverte').check();
  await expect(page.getByRole('row')).toHaveCount(2);
  await expect(page.getByRole('cell', { name: 'En retard' })).toBeVisible();

  await page.getByRole('link', { name: 'XYZ-2' }).click();
  await expect(page).toHaveURL(/\/shipments\/XYZ-2$/);
  await expect(page.getByRole('heading', { name: 'XYZ-2' })).toBeVisible();
  const scans = page
    .getByRole('list', { name: 'Frise des scans et des anomalies' })
    .getByRole('listitem');
  await expect(scans).toHaveCount(5);
  await expect(scans.filter({ hasText: 'Motif' })).toContainText('Motif : Destinataire absent');
  await expect(page.getByText('ouverte', { exact: true })).toBeVisible();
  await expect(page.getByText(/close le/)).toBeVisible();
});

test('détail d’une expédition inconnue', async ({ page }) => {
  await signIn(page);
  await mockProblem(page, '**/api/shipments/NOPE', 404, 'Shipment not found');
  await page.goto('/shipments/NOPE');
  await expect(page.getByRole('alert')).toHaveText('Expédition introuvable');
});

test('création : erreur 400 puis succès', async ({ page }) => {
  await signIn(page);
  let created = false;
  await mockApi(page, '**/api/shipments', { body: LIST }, 'GET');
  await mockProblem(page, '**/api/shipments', 400, 'expectedDeliveryAt: must not be null', {
    method: 'POST',
  });
  await page.goto('/shipments');
  await page.getByRole('button', { name: 'Créer une expédition' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Référence').fill('NEW-1');
  await dialog.getByLabel('Origine').fill('Paris');
  await dialog.getByLabel('Destination').fill('Nantes');
  await dialog.getByLabel('Livraison prévue').fill('2026-12-01T10:30');
  await dialog.getByRole('button', { name: 'Créer', exact: true }).click();
  await expect(dialog.getByRole('alert')).toHaveText('expectedDeliveryAt: must not be null');

  await page.unroute('**/api/shipments');
  await mockApi(page, '**/api/shipments', { body: LIST }, 'GET');
  await page.route('**/api/shipments', (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }
    created = true;
    return route.fulfill({ status: 201, json: shipment({ reference: 'NEW-1' }) });
  });
  await mockApi(page, '**/api/shipments/NEW-1', {
    body: { ...shipment({ reference: 'NEW-1' }), scans: [], anomalies: [] },
  });
  await dialog.getByRole('button', { name: 'Créer', exact: true }).click();
  await expect(page).toHaveURL(/\/shipments\/NEW-1$/);
  expect(created).toBe(true);
  await expect(page.getByRole('heading', { name: 'NEW-1' })).toBeVisible();
});
