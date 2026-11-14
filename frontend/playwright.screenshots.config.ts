import { defineConfig } from '@playwright/test';

// Captures des écrans pour /projet/ecrans : lancées à la main (`npm run screenshots`), hors de `npm run e2e`.
const port = Number(process.env['E2E_PORT'] ?? 4200);

export default defineConfig({
  testDir: 'screenshots',
  workers: 1,
  reporter: 'list',
  use: { baseURL: `http://localhost:${port}`, viewport: { width: 1280, height: 800 }, locale: 'fr-FR' },
  webServer: {
    command: `npx ng serve --port ${port}`,
    url: `http://localhost:${port}`,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
