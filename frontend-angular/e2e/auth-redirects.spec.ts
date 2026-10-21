import { expect, test } from '@playwright/test';

// Der BFF antwortet auf Seiten-Anfragen ohne Sitzungs-Cookies außer `/`, `/public/*` und Dateien mit Endung
// mit 301 auf `/`.
test.describe('Weiterleitung nicht angemeldeter Besucher', () => {
  const protectedRoutes = ['/agenda', '/settings', '/profile', '/friends', '/groups'];

  for (const route of protectedRoutes) {
    test(`${route} führt auf die Landingpage`, async ({ page }) => {
      await page.goto(route, { waitUntil: 'domcontentloaded' });
      await expect(page).toHaveURL('/');
      await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible();
    });
  }

  test('antwortet mit 301 auf /', async ({ request }) => {
    const response = await request.get('/agenda', { maxRedirects: 0 });
    expect(response.status()).toBe(301);
    expect(response.headers()['location']).toBe('/');
  });
});
