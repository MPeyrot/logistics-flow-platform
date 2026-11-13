import { defineConfig, devices } from '@playwright/test';

// E2E contre l'image nginx (lancée par scripts/image-e2e.sh) : pas de serveur de dev.
export default defineConfig({
  testDir: 'e2e-image',
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: { baseURL: process.env['IMAGE_URL'] ?? 'http://localhost:8088', trace: 'retain-on-failure' },
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'] } }],
});
