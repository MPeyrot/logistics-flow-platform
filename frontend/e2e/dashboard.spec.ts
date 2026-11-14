import { expect, test, type Page } from '@playwright/test';
import { mockApi } from './mock-api';

async function signIn(page: Page, role: 'ADMIN' | 'OPERATOR') {
  const payload = Buffer.from(JSON.stringify({ sub: 'alice', roles: [role] })).toString('base64url');
  const expiresAt = new Date(Date.now() + 3_600_000).toISOString();
  await page.addInitScript(
    ([token, exp]) => {
      localStorage.setItem('auth.token', token);
      localStorage.setItem('auth.expiresAt', exp);
    },
    [`e30.${payload}.sig`, expiresAt],
  );
}

const inHours = (h: number) => new Date(Date.now() + h * 3_600_000).toISOString();
const shipment = (over: Record<string, unknown>) => ({
  id: String(over['reference']),
  origin: 'Paris',
  destination: 'Lyon',
  status: 'IN_TRANSIT',
  createdAt: '2026-11-01T10:00:00Z',
  expectedDeliveryAt: inHours(120),
  openAnomalies: [],
  ...over,
});

const SHIPMENTS = [
  shipment({ reference: 'SOON-1', expectedDeliveryAt: inHours(5) }),
  shipment({ reference: 'LATER-2' }),
  shipment({ reference: 'DONE-3', status: 'DELIVERED', expectedDeliveryAt: inHours(5) }),
  shipment({ reference: 'DONE-4', status: 'DELIVERED' }),
];
const ANOMALIES = [
  { id: 'a1', shipmentReference: 'SOON-1', type: 'LATE', detectedAt: '2026-11-13T10:00:00Z', resolvedAt: null },
  { id: 'a2', shipmentReference: 'LATER-2', type: 'STALLED', detectedAt: '2026-11-14T10:00:00Z', resolvedAt: null },
  { id: 'a3', shipmentReference: 'DONE-3', type: 'STALLED', detectedAt: '2026-11-12T10:00:00Z', resolvedAt: null },
];

for (const role of ['ADMIN', 'OPERATOR'] as const) {
  test(`${role} : compteurs, anomalies, attendues sous 24 h`, async ({ page }) => {
    await signIn(page, role);
    await mockApi(page, '**/api/shipments', { body: SHIPMENTS }, 'GET');
    await mockApi(page, '**/api/anomalies?open=true', { body: ANOMALIES }, 'GET');
    let dltCalls = 0;
    await page.route('**/api/admin/dlt/scan-events', (route) => {
      dltCalls += 1;
      return route.fulfill({ json: [{ partition: 0, offset: 1 }, { partition: 1, offset: 4 }] });
    });

    await page.goto('/');
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('link', { name: 'Tableau de bord' }).first()).toBeVisible();
    await expect(page.getByTestId('status-DELIVERED')).toContainText('2');
    await expect(page.getByTestId('status-IN_TRANSIT')).toContainText('2');
    await expect(page.getByTestId('status-CREATED')).toContainText('0');
    await expect(page.getByTestId('count-LATE')).toHaveText('1');
    await expect(page.getByTestId('count-STALLED')).toHaveText('2');
    await expect(page.getByTestId('latest').getByRole('link').first()).toHaveText('LATER-2');
    await expect(page.getByTestId('due').getByRole('link')).toHaveText(['SOON-1']);

    if (role === 'ADMIN') {
      await expect(page.getByTestId('dlt-count')).toHaveText('2');
      await expect(page.getByRole('link', { name: /file des échecs/ })).toHaveAttribute('href', '/admin/dlt');
    } else {
      await expect(page.getByTestId('dlt-count')).toHaveCount(0);
      expect(dltCalls).toBe(0);
    }
  });
}

test('un statut mène à la liste filtrée', async ({ page }) => {
  await signIn(page, 'OPERATOR');
  await mockApi(page, '**/api/shipments', { body: SHIPMENTS }, 'GET');
  await mockApi(page, '**/api/anomalies?open=true', { body: ANOMALIES }, 'GET');
  await page.goto('/dashboard');
  await page.getByTestId('status-DELIVERED').click();
  await expect(page).toHaveURL(/\/shipments\?status=DELIVERED$/);
  await expect(page.getByRole('link', { name: 'DONE-3' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'SOON-1' })).toHaveCount(0);
});
