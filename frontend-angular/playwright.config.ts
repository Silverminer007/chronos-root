import { defineConfig, devices } from '@playwright/test';

const PORT = Number(process.env['E2E_PORT'] ?? 4300);

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  workers: process.env['CI'] ? 2 : undefined,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['BASE_URL'] ?? `http://localhost:${PORT}`,
    navigationTimeout: 15_000,
    actionTimeout: 10_000,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    // Q-01: Darstellung ab 360 px Breite
    { name: 'mobile-chrome', use: { ...devices['Pixel 5'] } },
  ],
  // Der BFF liefert das gebaute `dist/browser` aus (vorher `npm run build`). Das Backend und Keycloak werden nicht
  // gebraucht: Die Tests laufen ohne Anmeldung, nur die Pflicht-Variablen müssen gesetzt sein.
  webServer: process.env['BASE_URL']
    ? undefined
    : {
        command: 'node server/index.ts',
        url: `http://localhost:${PORT}/api/config`,
        reuseExistingServer: !process.env['CI'],
        env: {
          CHRONOS_PORT: String(PORT),
          CHRONOS_COOKIE_SECURE: 'false',
          CHRONOS_AUTH_ISSUER: 'http://localhost:1/realms/e2e',
          CHRONOS_AUTH_CLIENT_ID: 'e2e',
          CHRONOS_AUTH_REDIRECT_URI: `http://localhost:${PORT}/api/auth/callback`,
          CHRONOS_QUARKUS_URL: 'http://localhost:1',
          CHRONOS_PUBLIC_IMPRESSUM_NAME: 'Erika Mustermann',
          CHRONOS_PUBLIC_IMPRESSUM_STREET: 'Musterweg 1',
          CHRONOS_PUBLIC_IMPRESSUM_CITY: '12345 Musterstadt',
          CHRONOS_PUBLIC_IMPRESSUM_EMAIL: 'kontakt@example.org',
          CHRONOS_PUBLIC_IMPRESSUM_PHONE: '+49 123 456',
        },
      },
});
