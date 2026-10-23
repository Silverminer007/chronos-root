import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #230: Kopfzeile öffentlicher Seiten läuft bei 390 px über, 'Überspringen' im Onboarding ist
// unterstrichen, gestapelte Dialog-Buttons haben keinen Abstand.
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

test.describe('Parität zum alten Frontend (#230)', () => {
  for (const path of ['/', '/public/impressum', '/public/datenschutz']) {
    test(`Kopfzeile von ${path} bei 390px: kein horizontaler Überlauf`, async ({ page }) => {
      await page.goto(path, { waitUntil: 'domcontentloaded' });
      const header = page.locator('header').first();
      await expect(header.getByRole('link', { name: 'Registrieren' }).or(header.getByRole('button', { name: 'Registrieren' }))).toBeVisible();
      const res = await page.evaluate(() => {
        const h = document.querySelector('header')!;
        const vw = document.documentElement.clientWidth;
        const overflowing = [...h.querySelectorAll<HTMLElement>('a, button')]
          .map((e) => ({ text: e.textContent!.trim(), right: e.getBoundingClientRect().right }))
          .filter((e) => e.right > vw + 0.5);
        return { overflowing, scroll: document.documentElement.scrollWidth, vw };
      });
      expect(res.overflowing, 'Kopfzeilen-Buttons ragen über den Viewport hinaus').toEqual([]);
      expect(res.scroll, 'horizontaler Überlauf der Seite (scrollWidth <= clientWidth)').toBeLessThanOrEqual(res.vw);
    });
  }

  test("Onboarding: 'Überspringen' nicht unterstrichen", async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.addInitScript(() => localStorage.removeItem('onboardingShownAt'));
    await page.goto('/onboarding', { waitUntil: 'domcontentloaded' });
    const skip = page.getByRole('button', { name: 'Überspringen' });
    await expect(skip).toBeVisible();
    const deco = await skip.evaluate((el) => getComputedStyle(el).textDecorationLine);
    expect(deco, "text-decoration von 'Überspringen' (erwartet none)").toBe('none');
  });

  test('Dialog „Freund hinzufügen“: gestapelte Footer-Buttons mit Abstand >= 8px', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/friendships/friends', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests/incoming', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests/outgoing', (route) => route.fulfill({ json: [] }));
    await page.goto('/friends', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Hinzufügen' }).first().click();
    const dialog = page.getByRole('dialog', { name: 'Freund hinzufügen' });
    await expect(dialog).toBeVisible();
    const gap = await dialog.evaluate((el) => {
      const [a, b] = [...el.querySelectorAll('.modal-footer button')].map((x) => x.getBoundingClientRect());
      return Math.max(b.top - a.bottom, a.top - b.bottom);
    });
    expect(gap, 'Abstand zwischen gestapelten Dialog-Buttons in px (erwartet >= 8)').toBeGreaterThanOrEqual(8);
  });
});
