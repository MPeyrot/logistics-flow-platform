import { expect, test, type Page } from '@playwright/test';
import { mockApi, mockProblem } from './mock-api';

/** JWT non signé : le front ne lit que la charge. */
function token(claims: Record<string, unknown>): string {
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url');
  return `e30.${payload}.sig`;
}

async function mockLogin(page: Page, claims: Record<string, unknown>, lifetimeMs = 3_600_000) {
  await mockApi(
    page,
    '**/api/auth/token',
    {
      body: {
        accessToken: token(claims),
        tokenType: 'Bearer',
        expiresAt: new Date(Date.now() + lifetimeMs).toISOString(),
      },
    },
    'POST',
  );
}

async function fillAndSubmit(page: Page) {
  await page.getByLabel('Identifiant').fill('alice');
  await page.getByLabel('Mot de passe').fill('secret');
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test.describe('connexion et redirection par rôle', () => {
  for (const [role, url] of [
    ['ADMIN', '/shipments'],
    ['OPERATOR', '/shipments'],
    ['SCANNER', '/scan'],
  ] as const) {
    test(`${role} arrive sur ${url}`, async ({ page }) => {
      await mockLogin(page, { sub: 'alice', roles: [role], site: role === 'SCANNER' ? 'PAR-01' : undefined });
      await page.goto('/');
      await expect(page).toHaveURL(/\/login$/);
      await fillAndSubmit(page);
      await expect(page).toHaveURL(new RegExp(`${url}$`));
      await expect(page.getByTestId('user')).toContainText('alice');
    });
  }

  test('un identifiant incorrect affiche le message', async ({ page }) => {
    await mockProblem(page, '**/api/auth/token', 401, 'Bad credentials', { method: 'POST' });
    await page.goto('/login');
    await fillAndSubmit(page);
    await expect(page.getByRole('alert')).toHaveText('Identifiant ou mot de passe incorrect');
  });

  test('une URL protégée mène à la connexion puis revient (returnUrl)', async ({ page }) => {
    await mockLogin(page, { sub: 'alice', roles: ['ADMIN'] });
    await page.goto('/anomalies');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fanomalies/);
    await fillAndSubmit(page);
    await expect(page).toHaveURL(/\/anomalies$/);
  });

  test('une URL d’un autre rôle mène à « Accès refusé »', async ({ page }) => {
    await mockLogin(page, { sub: 'alice', roles: ['SCANNER'], site: 'PAR-01' });
    await page.goto('/login');
    await fillAndSubmit(page);
    await page.goto('/admin/users');
    await expect(page.getByRole('heading', { name: 'Accès refusé' })).toBeVisible();
  });

  test('la déconnexion renvoie à la connexion', async ({ page }) => {
    await mockLogin(page, { sub: 'alice', roles: ['ADMIN'] });
    await page.goto('/login');
    await fillAndSubmit(page);
    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByTestId('user')).toHaveCount(0);
  });

  test('la déconnexion est synchronisée entre onglets', async ({ page, context }) => {
    await mockLogin(page, { sub: 'alice', roles: ['ADMIN'] });
    await page.goto('/login');
    await fillAndSubmit(page);
    await expect(page).toHaveURL(/\/shipments$/);

    const other = await context.newPage();
    await other.goto('/shipments');
    await expect(other.getByTestId('user')).toBeVisible();
    await other.getByRole('button', { name: 'Se déconnecter' }).click();

    await expect(page).toHaveURL(/\/login$/);
  });
});

test('429 sur la connexion : compte à rebours, bouton désactivé puis réactivé', async ({ page }) => {
  await mockProblem(page, '**/api/auth/token', 429, 'Trop de tentatives', {
    method: 'POST',
    headers: { 'Retry-After': '2' },
  });
  await page.goto('/login');
  await fillAndSubmit(page);
  await expect(page.getByText(/Réessayez dans \d s/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeDisabled();
  await expect(page.getByText(/Réessayez dans/)).toHaveCount(0, { timeout: 5000 });
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeDisabled(); // mot de passe vidé
  await page.getByLabel('Mot de passe').fill('secret');
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeEnabled();
});

test('expiration : la session est effacée et la connexion affiche « Session expirée »', async ({ page }) => {
  await mockLogin(page, { sub: 'alice', roles: ['ADMIN'] }, 2000);
  await page.goto('/login');
  await fillAndSubmit(page);
  await expect(page).toHaveURL(/\/shipments$/);
  await expect(page).toHaveURL(/\/login/, { timeout: 5000 });
  await expect(page.getByText('Session expirée')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('auth.token'))).toBeNull();
});

test('un jeton expiré au démarrage est effacé', async ({ page }) => {
  await page.addInitScript(
    ([t, e]) => {
      localStorage.setItem('auth.token', t);
      localStorage.setItem('auth.expiresAt', e);
    },
    [token({ sub: 'alice', roles: ['ADMIN'] }), new Date(Date.now() - 1000).toISOString()],
  );
  await page.goto('/shipments');
  await expect(page).toHaveURL(/\/login/);
  expect(await page.evaluate(() => localStorage.getItem('auth.token'))).toBeNull();
});

test('l’en-tête reste utilisable à 360 px (pas de défilement horizontal)', async ({ page }) => {
  await mockLogin(page, { sub: 'alice-avec-un-long-identifiant', roles: ['ADMIN'] });
  await page.setViewportSize({ width: 360, height: 740 });
  await page.goto('/login');
  await fillAndSubmit(page);
  await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible();
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  expect(overflow).toBe(false);
});
