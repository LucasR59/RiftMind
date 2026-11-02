import { defineConfig, devices } from '@playwright/test'

/**
 * Tests de bout en bout (tâche 8.1) : le vrai front, le vrai backend et la base, avec une fausse API Riot (WireMock).
 * La pile doit tourner avant (voir e2e/README.md à la racine du dépôt) :
 *
 *   RIOT_BASE_URL=http://wiremock:8080 docker compose --profile e2e up -d
 *   npm run test:e2e
 *
 * `E2E_BASE_URL` change l'adresse du front ; `PW_CHANNEL=msedge` utilise Edge (ou Chrome) déjà installé au lieu du
 * Chromium de Playwright (`npx playwright install chromium`).
 */
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 60_000,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], channel: process.env.PW_CHANNEL },
    },
    {
      name: 'mobile',
      use: {
        ...devices['Desktop Chrome'],
        channel: process.env.PW_CHANNEL,
        viewport: { width: 390, height: 844 },
      },
    },
  ],
})
