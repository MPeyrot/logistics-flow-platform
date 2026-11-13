import type { Page } from '@playwright/test';

export interface MockResponse {
  /** Statut HTTP ; 200 par défaut. */
  status?: number;
  /** Corps JSON (sérialisé). */
  body?: unknown;
  /** En-têtes de réponse, par exemple `Retry-After`. */
  headers?: Record<string, string>;
}

/** Simule une route de l'API (`page.route`) ; `url` est un motif glob ou une expression régulière. */
export async function mockApi(
  page: Page,
  url: string | RegExp,
  response: MockResponse,
  method?: string,
): Promise<void> {
  await page.route(url, (route) => {
    if (method && route.request().method() !== method.toUpperCase()) {
      return route.fallback();
    }
    return route.fulfill({
      status: response.status ?? 200,
      contentType: 'application/json',
      headers: response.headers,
      body: response.body === undefined ? undefined : JSON.stringify(response.body),
    });
  });
}

/** Réponse d'erreur `application/problem+json`, comme celles des services. */
export async function mockProblem(
  page: Page,
  url: string | RegExp,
  status: number,
  detail: string,
  options: { headers?: Record<string, string>; method?: string } = {},
): Promise<void> {
  await page.route(url, (route) => {
    if (options.method && route.request().method() !== options.method.toUpperCase()) {
      return route.fallback();
    }
    return route.fulfill({
      status,
      contentType: 'application/problem+json',
      headers: options.headers,
      body: JSON.stringify({ type: 'about:blank', title: String(status), status, detail }),
    });
  });
}
