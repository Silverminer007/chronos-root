import { expect, test } from '@playwright/test';

test.describe('Öffentliche Seiten', () => {
  test('Impressum zeigt Überschrift und die Konfiguration des BFF', async ({ page }) => {
    await page.goto('/public/impressum', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Impressum', level: 1 })).toBeVisible();
    await expect(page.getByText('Erika Mustermann')).toBeVisible();
    await expect(page.getByText('Musterweg 1')).toBeVisible();
    await expect(page.getByText('12345 Musterstadt')).toBeVisible();
    await expect(page.getByRole('link', { name: 'kontakt@example.org' })).toHaveAttribute(
      'href',
      'mailto:kontakt@example.org',
    );
    await expect(page.getByRole('link', { name: '+49 123 456' })).toHaveAttribute('href', 'tel:+49 123 456');
  });

  test('Logo-Link führt zurück zur Startseite', async ({ page }) => {
    await page.goto('/public/impressum', { waitUntil: 'domcontentloaded' });
    const logoLink = page.getByRole('link', { name: /Chronos/ }).first();
    await expect(logoLink).toBeVisible();
    await logoLink.click();
    await expect(page).toHaveURL('/');
  });

  test('Datenschutz zeigt Überschrift und den Verantwortlichen', async ({ page }) => {
    await page.goto('/public/datenschutz', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Datenschutzerklärung', level: 1 })).toBeVisible();
    await expect(page.getByText('Erika Mustermann')).toBeVisible();
  });

  test('Installationsseite zeigt die Anleitungen', async ({ page }) => {
    await page.goto('/public/install', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Chronos installieren' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Safari', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Was ist eine PWA?' })).toBeVisible();
  });

  test('Seiten laufen ohne horizontales Scrollen (Q-01)', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    for (const path of ['/', '/public/impressum', '/public/datenschutz', '/public/install']) {
      await page.goto(path, { waitUntil: 'domcontentloaded' });
      await expect(page.locator('h1')).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow, path).toBeLessThanOrEqual(0);
    }
  });
});

test.describe('PWA-Manifest', () => {
  test('wird mit passendem Content-Type ausgeliefert', async ({ request }) => {
    const response = await request.get('/manifest.webmanifest');
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('application/manifest+json');
  });

  test('enthält die Pflichtfelder', async ({ request }) => {
    const manifest = await (await request.get('/manifest.webmanifest')).json();
    expect(manifest.name).toBeTruthy();
    expect(Array.isArray(manifest.icons)).toBe(true);
    expect(manifest.start_url).toBeDefined();
  });
});
