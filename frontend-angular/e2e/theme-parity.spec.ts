import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #210: Das SCSS-Theme weicht vom alten Nuxt-Frontend ab (Hintergrund gray-50, Slate-Texte, Markenlila
// statt Bootstrap-Blau, grau gefüllte Eingabefelder, größere Kartenrundung mit Schatten).
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

const css = (page: import('@playwright/test').Page, selector: string, prop: string) =>
  page.locator(selector).first().evaluate((el, p) => getComputedStyle(el).getPropertyValue(p), prop);

test.describe('Theme-Parität zum alten Frontend (#210)', () => {
  test('Landing: Seitenhintergrund gray-50 und Überschrift gray-900', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible();
    expect(await css(page, 'body', 'background-color'), 'Seitenhintergrund (erwartet gray-50)').toBe('rgb(249, 250, 251)');
    expect(await css(page, 'h2', 'color'), 'Überschrift (erwartet gray-900)').toBe('rgb(17, 24, 39)');
    expect(await css(page, 'p', 'color'), 'Fließtext (erwartet gray-600)').toBe('rgb(75, 85, 99)');
  });

  test('Agenda: Karte mit großer Rundung und Schatten', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/appointments?**', (route) =>
      route.fulfill({
        json: {
          data: [
            {
              id: 1, name: 'Zeltlager', description: null, start: '2026-10-21T16:00:00Z', end: '2026-10-21T19:00:00Z',
              venue: null, status: 'PLANNED', minimal_attendees: 0, participants: [], group_participants: [], messages: [],
            },
          ],
          meta: { page: 0, size: 20, total: 1 },
        },
      }),
    );
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.appointment-card').first()).toBeVisible();
    const radius = parseFloat(await css(page, '.appointment-card', 'border-top-left-radius'));
    expect(radius, 'Kartenrundung in px (erwartet >= 16)').toBeGreaterThanOrEqual(16);
    expect(await css(page, '.appointment-card', 'box-shadow'), 'Kartenschatten (erwartet vorhanden)').not.toBe('none');
  });

  test('Markenfarbe: --bs-primary ist Lila statt Bootstrap-Blau und Eingabefelder sind grau gefüllt', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible();
    const primary = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--bs-primary').trim());
    expect(primary, 'Markenfarbe purple-600').toBe('#9333ea');
    const input = await page.evaluate(() => {
      const el = document.createElement('input');
      el.className = 'form-control';
      document.body.appendChild(el);
      const s = getComputedStyle(el);
      return { bg: s.backgroundColor, radius: parseFloat(s.borderTopLeftRadius) };
    });
    expect(input.bg, 'Eingabefeld grau gefüllt (erwartet gray-50)').toBe('rgb(249, 250, 251)');
    expect(input.radius, 'Eingabefeld-Rundung (erwartet >= 12)').toBeGreaterThanOrEqual(12);
  });
});
