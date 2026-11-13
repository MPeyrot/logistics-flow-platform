import { expect, test } from '@playwright/test';
import { mockApi, mockProblem } from './mock-api';

test('l’application démarre', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Logistics Flow' })).toBeVisible();
});

test('l’aide simule une réponse JSON et un problem+json avec ses en-têtes', async ({ page }) => {
  await mockApi(page, '**/api/ping', { body: { ok: true } });
  await mockProblem(page, '**/api/limite', 429, 'Trop de demandes', {
    headers: { 'Retry-After': '30' },
  });
  await page.goto('/');

  const results = await page.evaluate(async () => {
    const ok = await fetch('/api/ping');
    const limited = await fetch('/api/limite');
    return {
      okStatus: ok.status,
      okBody: await ok.json(),
      limitedStatus: limited.status,
      limitedType: limited.headers.get('content-type'),
      retryAfter: limited.headers.get('retry-after'),
      detail: (await limited.json()).detail,
    };
  });

  expect(results).toEqual({
    okStatus: 200,
    okBody: { ok: true },
    limitedStatus: 429,
    limitedType: 'application/problem+json',
    retryAfter: '30',
    detail: 'Trop de demandes',
  });
});
