import { expect, test, type Page } from '@playwright/test';

const CSP =
  "default-src 'self'; script-src 'self'; object-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

/** Messages de console qui parlent d'une violation de la CSP. */
function watchCsp(page: Page): string[] {
  const violations: string[] = [];
  page.on('console', (m) => {
    if (/content security policy|refused to/i.test(m.text())) violations.push(m.text());
  });
  page.on('pageerror', (e) => {
    if (/content security policy/i.test(e.message)) violations.push(e.message);
  });
  return violations;
}

test('l’en-tête CSP exacte et les en-têtes de sécurité accompagnent la page', async ({
  request,
}) => {
  const res = await request.get('/');
  expect(res.status()).toBe(200);
  const h = res.headers();
  expect(h['content-security-policy']).toBe(CSP);
  expect(h['x-content-type-options']).toBe('nosniff');
  expect(h['referrer-policy']).toBe('no-referrer');
  expect(h['strict-transport-security']).toContain('max-age=');
  expect(h['cache-control']).toBe('no-cache');
});

// Chaque `location` inclut le snippet elle-même (nginx ne transmet pas les `add_header` du `server`) :
// une location qui l'oublierait perdrait la CSP.
test('chaque location, proxys compris, porte la CSP exacte', async ({ request }) => {
  for (const path of [
    '/',
    '/index.html',
    '/route-inconnue',
    '/api/scans',
    '/api/shipments',
    '/.well-known/jwks.json',
  ]) {
    const res = await request.get(path);
    expect(res.headers()['content-security-policy'], path).toBe(CSP);
  }
});

test('les fichiers hachés sont mis en cache sans limite, avec les en-têtes de sécurité', async ({
  request,
}) => {
  const html = await (await request.get('/')).text();
  const script = /src="([^"]+\.js)"/.exec(html)?.[1];
  expect(script).toBeTruthy();
  const res = await request.get(`/${script}`.replace('//', '/'));
  expect(res.status()).toBe(200);
  expect(res.headers()['cache-control']).toBe('public, max-age=31536000, immutable');
  expect(res.headers()['content-security-policy']).toBe(CSP);
});

test('la page de connexion s’affiche sans violation de la CSP', async ({ page }) => {
  const violations = watchCsp(page);
  await page.goto('/login');
  await expect(page.getByLabel('Identifiant')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible();
  expect(violations).toEqual([]);
});

test('un écran Material après connexion s’affiche sans violation de la CSP', async ({ page }) => {
  const violations = watchCsp(page);
  const claims = Buffer.from(JSON.stringify({ sub: 'alice', roles: ['ADMIN'] })).toString(
    'base64url',
  );
  await page.route('**/api/auth/token', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: `e30.${claims}.sig`,
        tokenType: 'Bearer',
        expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      }),
    }),
  );
  await page.route('**/api/shipments**', (route) =>
    route.fulfill({ contentType: 'application/json', body: '[]' }),
  );
  await page.goto('/login');
  await page.getByLabel('Identifiant').fill('alice');
  await page.getByLabel('Mot de passe').fill('secret');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/shipments$/);
  await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible();
  expect(violations).toEqual([]);
});

test('une route Angular sans fichier retombe sur index.html (200)', async ({ request }) => {
  const res = await request.get('/shipments');
  expect(res.status()).toBe(200);
  expect(res.headers()['content-type']).toContain('text/html');
  expect(await res.text()).toContain('<app-root');
});

test('/api/scans et ses sous-chemins vont vers ingestion-service, Host de l’amont compris', async ({
  request,
}) => {
  for (const path of ['/api/scans', '/api/scans/imports?x=1']) {
    const res = await request.get(path);
    expect(await res.json()).toEqual({
      upstream: 'ingestion',
      path,
      host: 'ingestion-up:9000',
    });
  }
});

test('le reste de /api/ et /.well-known/ vont vers shipment-service', async ({ request }) => {
  for (const path of ['/api/shipments', '/api/auth/token', '/.well-known/jwks.json']) {
    const res = await request.get(path);
    expect(await res.json()).toEqual({ upstream: 'shipment', path, host: 'shipment-up:9000' });
  }
  // « scansion » n'est pas un sous-chemin de /api/scans.
  expect(
    ((await (await request.get('/api/scansion')).json()) as { upstream: string }).upstream,
  ).toBe('shipment');
});
