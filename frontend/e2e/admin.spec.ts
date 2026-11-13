import { expect, test, type Page } from '@playwright/test';
import { mockApi } from './mock-api';

/** Ouvre une session ADMIN (`sub` = admin) sans passer par l'écran de connexion. */
async function signIn(page: Page) {
  const payload = Buffer.from(JSON.stringify({ sub: 'admin', roles: ['ADMIN'] })).toString(
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

const account = (over: Record<string, unknown>) => ({
  id: String(over['username']),
  role: 'OPERATOR',
  site: null,
  active: true,
  createdAt: '2026-11-01T10:00:00Z',
  ...over,
});

test('création d’un SCANNER avec son site', async ({ page }) => {
  await signIn(page);
  const users = [account({ username: 'admin', role: 'ADMIN' })];
  let posted: unknown;
  await page.route('**/api/users', async (route) => {
    if (route.request().method() === 'POST') {
      posted = route.request().postDataJSON();
      const created = account({ ...(posted as object), username: 'lea', password: undefined });
      users.push(created);
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      });
    }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(users) });
  });

  await page.goto('/admin/users');
  await expect(page.getByRole('cell', { name: 'admin', exact: true })).toBeVisible();
  // Pas d'interrupteur sur le compte connecté.
  await expect(page.getByRole('switch')).toHaveCount(0);

  await page.getByRole('button', { name: 'Créer un compte' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Identifiant').fill('lea');
  await dialog.getByLabel('Mot de passe', { exact: true }).fill('un-mot-de-passe-long');
  await dialog.getByLabel('Confirmation du mot de passe').fill('un-mot-de-passe-long');
  await expect(dialog.getByLabel('Site')).toHaveCount(0);
  await dialog.getByLabel('Rôle').selectOption('SCANNER');
  await expect(dialog.getByRole('button', { name: 'Créer', exact: true })).toBeDisabled();
  await dialog.getByLabel('Site').fill('PAR-01');
  await dialog.getByRole('button', { name: 'Créer', exact: true }).click();

  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('cell', { name: 'lea', exact: true })).toBeVisible();
  expect(posted).toEqual({
    username: 'lea',
    password: 'un-mot-de-passe-long',
    role: 'SCANNER',
    site: 'PAR-01',
  });
  await expect(page.getByRole('switch')).toHaveCount(1);
});

test('« tout rejouer » suivi jusqu’à la fin malgré un rechargement', async ({ page }) => {
  await signIn(page);
  await mockApi(
    page,
    '**/api/admin/dlt/scan-events',
    {
      body: [
        {
          partition: 0,
          offset: 7,
          timestamp: '2026-11-13T10:00:00Z',
          key: 'REF-1',
          value: '{"eventId":"e1"}',
          exceptionClass: 'org.example.InvalidScanException',
          exceptionMessage: 'Transition interdite',
          replayed: false,
          replayedAt: null,
        },
      ],
    },
    'GET',
  );
  await mockApi(
    page,
    '**/api/admin/dlt/scan-events/replay',
    {
      status: 202,
      body: { id: 'job-1', finished: false, replayed: 0, published: 0, failed: 0, error: null },
    },
    'POST',
  );
  let polls = 0;
  await page.route('**/api/admin/dlt/scan-events/replay/job-1', (route) => {
    polls += 1;
    const finished = polls >= 5;
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'job-1',
        finished,
        replayed: finished ? 3 : 1,
        published: finished ? 3 : 0,
        failed: 0,
        error: null,
      }),
    });
  });

  await page.goto('/admin/dlt');
  await expect(page.getByRole('cell', { name: /InvalidScanException/ })).toBeVisible();
  await page.getByRole('button', { name: 'Valeur' }).click();
  await expect(page.locator('pre')).toContainText('"eventId": "e1"');

  await page.getByRole('button', { name: 'Tout rejouer' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Tout rejouer' }).click();
  await expect(page.getByText('En cours : 1 rejoués')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('dlt.replayAllId'))).toBe('job-1');

  await page.reload();
  await expect(page.getByText('En cours : 1 rejoués')).toBeVisible();
  await expect(page.getByText('Terminé : 3 rejoués, 3 publiés, 0 en échec.')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('dlt.replayAllId'))).toBeNull();
});

async function usersWithLea(page: Page, patchStatus?: number) {
  await signIn(page);
  const users = [account({ username: 'admin', role: 'ADMIN' }), account({ username: 'lea' })];
  await page.route('**/api/users', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(users) }),
  );
  await page.route('**/api/users/lea', (route) =>
    route.fulfill(
      patchStatus
        ? {
            status: patchStatus,
            contentType: 'application/problem+json',
            body: JSON.stringify({ detail: 'Conflit' }),
          }
        : { contentType: 'application/json', body: JSON.stringify(users[1]) },
    ),
  );
  await page.goto('/admin/users');
  const toggle = page.getByRole('switch', { name: 'Actif' });
  await expect(toggle).toBeChecked();
  await toggle.click();
  await page.getByRole('dialog').waitFor();
  return toggle;
}

test('annuler la désactivation remet l’interrupteur sur « actif »', async ({ page }) => {
  const toggle = await usersWithLea(page);
  await page.getByRole('dialog').getByRole('button', { name: 'Annuler' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(toggle).toBeChecked();
});

test('un 409 sur la désactivation remet l’interrupteur sur « actif »', async ({ page }) => {
  const toggle = await usersWithLea(page, 409);
  await page.getByRole('dialog').getByRole('button', { name: 'Désactiver' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(toggle).toBeChecked();
});
