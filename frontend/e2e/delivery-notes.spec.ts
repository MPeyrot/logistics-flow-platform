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

const pdf = { name: 'bon.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') };

const USAGE = { inputTokens: 1200, outputTokens: 85, cacheWriteTokens: 0, cacheReadTokens: 0 };

const NOTE = {
  reference: 'BL-42',
  deliveryDate: '2026-12-05',
  senderName: 'ACME',
  recipientName: 'Jeanne',
  recipientAddress: '1 rue des Lilas, Lyon',
  packageCount: 3,
  totalWeightKg: 12.5,
};

test('bon sans expédition : création préremplie', async ({ page }) => {
  await signIn(page);
  await mockApi(page, '**/api/delivery-notes/extract', {
    body: { deliveryNote: NOTE, costUsd: 0.0004, usage: USAGE, shipment: null },
  });
  await page.goto('/delivery-notes');
  await page.getByLabel('Fichier du bon').setInputFiles(pdf);

  await expect(page.getByText('BL-42', { exact: true })).toBeVisible();
  await expect(page.getByText(/0,0004\s\$/)).toBeVisible();
  await expect(page.getByText('1200 en entrée · 85 en sortie')).toBeVisible();
  await expect(page.getByText('Aucune expédition pour cette référence')).toBeVisible();

  await page.getByRole('button', { name: 'Créer l’expédition' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Référence')).toHaveValue('BL-42');
  await expect(dialog.getByLabel('Livraison prévue')).toHaveValue('2026-12-05T18:00');
  await expect(dialog.getByLabel('Origine')).toHaveValue('');
  await expect(dialog.getByLabel('Destination')).toHaveValue('');
});

test('bon avec expédition : lien vers le détail', async ({ page }) => {
  await signIn(page);
  await mockApi(page, '**/api/delivery-notes/extract', {
    body: {
      deliveryNote: NOTE,
      costUsd: 0.0004,
      usage: USAGE,
      shipment: {
        id: 's1',
        reference: 'BL-42',
        origin: 'Paris',
        destination: 'Lyon',
        status: 'IN_TRANSIT',
        createdAt: '2026-11-01T10:00:00Z',
        expectedDeliveryAt: '2026-12-05T10:00:00Z',
        openAnomalies: [],
      },
    },
  });
  await page.goto('/delivery-notes');
  await page.getByLabel('Fichier du bon').setInputFiles(pdf);
  await expect(page.getByRole('link', { name: 'BL-42' })).toHaveAttribute(
    'href',
    '/shipments/BL-42',
  );
});

test('fichier refusé côté client, puis 429 avec compte à rebours', async ({ page }) => {
  await signIn(page);
  await mockProblem(page, '**/api/delivery-notes/extract', 429, 'quota', {
    headers: { 'Retry-After': '30' },
  });
  await page.goto('/delivery-notes');

  await page
    .getByLabel('Fichier du bon')
    .setInputFiles({ name: 'bon.docx', mimeType: 'application/msword', buffer: Buffer.from('x') });
  await expect(page.getByRole('alert')).toContainText('Format non accepté');

  await page.getByLabel('Fichier du bon').setInputFiles(pdf);
  await expect(page.getByText(/Réessayez dans \d+ s/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Choisir un fichier' })).toBeDisabled();
});
