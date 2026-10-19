import { defineConfig, devices } from '@playwright/test';

/**
 * UI end-to-end suite. Every /api call is answered by an in-memory mock
 * (e2e/fixtures/mock-api.ts), so the suite needs no backend, Postgres,
 * Ollama or LLM key and is fully deterministic -- it tests the Angular UI,
 * not the RAG answers. Live-stack checks are a separate manual pass.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'e2e-report' }]],
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // Optional: point at a preinstalled Chromium instead of `npx playwright install`.
    launchOptions: process.env['PW_CHROMIUM_PATH'] ? { executablePath: process.env['PW_CHROMIUM_PATH'] } : {},
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] }, grep: /@responsive/ },
  ],
  webServer: {
    command: 'npx ng serve --port 4300',
    url: 'http://localhost:4300',
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
