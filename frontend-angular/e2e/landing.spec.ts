import { expect, test } from '@playwright/test';

test.describe('Landingpage', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
  });

  test('zeigt Überschrift und Tagline', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible();
    await expect(page.getByText('Für Jugendverbände gemacht')).toBeVisible();
  });

  test('zeigt Anmelden und Registrieren', async ({ page }) => {
    await expect(page.getByRole('link', { name: 'Anmelden' }).first()).toBeVisible();
    await expect(page.getByRole('link', { name: 'Registrieren' }).first()).toBeVisible();
  });

  test('Anmelden verlinkt auf /api/auth/login', async ({ page }) => {
    await expect(page.getByRole('link', { name: 'Anmelden' }).first()).toHaveAttribute('href', '/api/auth/login');
  });

  test('Registrieren verlinkt auf /api/auth/register', async ({ page }) => {
    await expect(page.getByRole('link', { name: 'Registrieren' }).first()).toHaveAttribute(
      'href',
      '/api/auth/register',
    );
  });

  test('zeigt den Funktionsbereich mit vier Karten', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Alles, was dein Team braucht' })).toBeVisible();
    for (const title of ['Termine organisieren', 'Verfügbarkeit abfragen', 'Push-Benachrichtigungen', 'Einfache Zusagen']) {
      await expect(page.getByRole('heading', { name: title })).toBeVisible();
    }
  });

  test('zeigt die drei Demo-Termine', async ({ page }) => {
    for (const name of ['Sommerfest', 'Team-Meeting', 'Gruppenstunde']) {
      await expect(page.getByRole('heading', { name })).toBeVisible();
    }
  });

  test('zeigt den Abschlussbereich', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Bereit loszulegen?' })).toBeVisible();
  });

  test('verlinkt im Footer auf Impressum und Datenschutz', async ({ page }) => {
    await expect(page.getByRole('link', { name: 'Impressum' })).toHaveAttribute('href', '/public/impressum');
    await expect(page.getByRole('link', { name: 'Datenschutz' })).toHaveAttribute('href', '/public/datenschutz');
  });
});
