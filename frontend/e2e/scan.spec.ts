import { expect, test, type Page } from '@playwright/test';
import { mockApi, mockProblem } from './mock-api';

/** Ouvre une session SCANNER du site LYO sans passer par l'écran de connexion. */
async function signIn(page: Page) {
  const payload = Buffer.from(JSON.stringify({ sub: 'sam', roles: ['SCANNER'], site: 'LYO' })).toString(
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

test('deux scans enchaînés, dont un échec avec motif renvoyé avec le même eventId', async ({ page }) => {
  await signIn(page);
  const bodies: Record<string, unknown>[] = [];
  let failNext = true;
  await page.route('**/api/scans', async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }
    const body = route.request().postDataJSON() as Record<string, unknown>;
    bodies.push(body);
    if (body['type'] === 'DELIVERY_FAILED' && failNext) {
      failNext = false;
      return route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ status: 503, detail: 'Scan could not be published, retry later' }),
      });
    }
    return route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({ eventId: body['eventId'] }),
    });
  });

  await page.goto('/scan');
  await expect(page.getByText('Site : LYO')).toBeVisible();
  const reference = page.getByLabel('Référence de l’expédition');
  await expect(reference).toBeFocused();
  await expect(page.getByLabel('Motif (facultatif)')).toHaveCount(0);

  // Premier scan : Entrée envoie.
  await reference.fill('EXP-1');
  await reference.press('Enter');
  await expect(page.getByRole('status').filter({ hasText: 'Scan envoyé' })).toBeVisible();
  await expect(reference).toHaveValue('');
  await expect(reference).toBeFocused();
  await expect(page.getByLabel('Derniers scans').getByText('EXP-1')).toBeVisible();

  // Second scan : échec de livraison avec motif ; premier envoi refusé (503), renvoi accepté.
  await page.getByLabel('Type de scan').selectOption('DELIVERY_FAILED');
  await reference.fill('EXP-2');
  await page.getByLabel('Motif (facultatif)').fill('Destinataire absent');
  await expect(page.getByText('19 / 200')).toBeVisible();
  await page.getByRole('button', { name: 'Envoyer' }).click();
  await expect(page.getByRole('alert')).toContainText('503');
  await page.getByRole('button', { name: 'Envoyer' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Scan envoyé' })).toBeVisible();

  expect(bodies).toHaveLength(3);
  expect(bodies[0]).toMatchObject({ shipmentReference: 'EXP-1', type: 'PICKED_UP', site: 'LYO' });
  expect(bodies[0]).not.toHaveProperty('reason');
  expect(bodies[1]).toMatchObject({
    shipmentReference: 'EXP-2',
    type: 'DELIVERY_FAILED',
    reason: 'Destinataire absent',
  });
  expect(bodies[2]['eventId']).toBe(bodies[1]['eventId']);
  expect(bodies[1]['eventId']).not.toBe(bodies[0]['eventId']);

  // Le type est gardé, la liste de session a les deux scans.
  await expect(page.getByLabel('Type de scan')).toHaveValue('DELIVERY_FAILED');
  await expect(page.getByLabel('Derniers scans').getByRole('listitem')).toHaveCount(2);
});

test('un motif trop long bloque l’envoi', async ({ page }) => {
  await signIn(page);
  await page.goto('/scan');
  await page.getByLabel('Type de scan').selectOption('DELIVERY_FAILED');
  await page.getByLabel('Référence de l’expédition').fill('EXP-3');
  await page.getByLabel('Motif (facultatif)').fill('x'.repeat(201));
  await expect(page.getByRole('alert')).toContainText('200');
  await expect(page.getByRole('button', { name: 'Envoyer' })).toBeDisabled();
});

test('saisie différée : la date est envoyée', async ({ page }) => {
  await signIn(page);
  let sent: Record<string, unknown> | undefined;
  await page.route('**/api/scans', (route) => {
    sent = route.request().postDataJSON() as Record<string, unknown>;
    return route.fulfill({ status: 202, contentType: 'application/json', body: '{"eventId":"x"}' });
  });
  await page.goto('/scan');
  await page.getByText('Saisie différée').click();
  await page.getByLabel('Date et heure du scan').fill('2026-11-10T08:30');
  await page.getByLabel('Référence de l’expédition').fill('EXP-4');
  await page.getByRole('button', { name: 'Envoyer' }).click();
  await expect(page.getByText('Scan envoyé')).toBeVisible();
  expect(new Date(String(sent?.['occurredAt'])).getTime()).toBe(new Date('2026-11-10T08:30').getTime());
});

const csv = (name: string, content = 'shipmentReference,type,site,occurredAt\n') => ({
  name,
  mimeType: 'text/csv',
  buffer: Buffer.from(content),
});

test('import avec lignes rejetées : rapport et téléchargement', async ({ page }) => {
  await signIn(page);
  await mockApi(page, '**/api/scans/imports', {
    body: {
      lines: 150,
      published: 40,
      rejected: 110,
      errors: Array.from({ length: 100 }, (_, i) => ({ line: i + 2, detail: `type: invalide ${i}` })),
    },
  });
  await page.goto('/scan/import');

  const [template] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Télécharger un modèle' }).click(),
  ]);
  expect(template.suggestedFilename()).toBe('modele-scans.csv');

  await page.getByLabel('Fichier CSV').setInputFiles(csv('scans.csv'));
  const report = page.getByLabel('Rapport d’import');
  await expect(report.getByRole('definition').filter({ hasText: '150' })).toBeVisible();
  await expect(report.getByRole('definition').filter({ hasText: '40' })).toBeVisible();
  await expect(report.getByText('100 premières erreurs sur 110')).toBeVisible();
  await expect(report.getByRole('row')).toHaveCount(101);

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Télécharger le rapport' }).click(),
  ]);
  expect(download.suggestedFilename()).toBe('rapport-import.csv');
});

test('import : mauvaise extension refusée avant l’envoi', async ({ page }) => {
  await signIn(page);
  await page.goto('/scan/import');
  await page.getByLabel('Fichier CSV').setInputFiles(csv('scans.txt'));
  await expect(page.getByRole('alert')).toContainText('.csv');
});

test('import : 503 dit combien de scans sont déjà publiés', async ({ page }) => {
  await signIn(page);
  await page.route('**/api/scans/imports', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ status: 503, detail: 'Kafka down', published: 2000 }),
    }),
  );
  await page.goto('/scan/import');
  await page.getByLabel('Fichier CSV').setInputFiles(csv('scans.csv'));
  await expect(page.getByRole('alert')).toContainText(
    'Kafka indisponible, 2000 scans déjà publiés ; réimporter le fichier ne créera pas de doublon',
  );
});

test('import : 429 affiche un compte à rebours', async ({ page }) => {
  await signIn(page);
  await mockProblem(page, '**/api/scans/imports', 429, 'Another import is running', {
    headers: { 'Retry-After': '30' },
  });
  await page.goto('/scan/import');
  await page.getByLabel('Fichier CSV').setInputFiles(csv('scans.csv'));
  await expect(page.getByText(/Réessayez dans \d+ s/).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Choisir un fichier' })).toBeDisabled();
});
