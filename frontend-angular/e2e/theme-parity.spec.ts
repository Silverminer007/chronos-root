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
    expect(await css(page, '.glass-card h2', 'color'), 'Überschrift (erwartet gray-900)').toBe('rgb(17, 24, 39)');
    expect(await css(page, '.glass-card p.text-body-secondary', 'color'), 'Fließtext (erwartet gray-600)').toBe('rgb(75, 85, 99)');
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

  test('Auswahlzustände: .btn-check:checked, .active und Hover sind Markenlila mit Rahmen, gefüllte Buttons unberührt', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible();
    const res = await page.evaluate(() => {
      document.body.insertAdjacentHTML(
        'beforeend',
        `<div id="t"><input type="radio" class="btn-check" id="r1" name="r" checked><label class="btn btn-outline-primary border-2" for="r1">a</label>
         <button class="btn btn-outline-primary border-2 active">b</button><button class="btn btn-outline-primary">c</button>
         <button class="btn btn-primary active">d</button><button class="btn btn-success active">e</button></div>`,
      );
      const s = (sel: string) => {
        const c = getComputedStyle(document.querySelector(`#t ${sel}`) as Element);
        return { color: c.color, border: c.borderTopColor, bg: c.backgroundColor, width: c.borderTopWidth };
      };
      return { checked: s('label'), active: s('button.active.btn-outline-primary'), idle: s('button.btn-outline-primary:not(.active)'), filled: s('.btn-primary'), success: s('.btn-success') };
    });
    const purple = 'rgb(147, 51, 234)';
    for (const state of [res.checked, res.active]) {
      expect(state.color, 'Auswahl: Text lila').toBe(purple);
      expect(state.border, 'Auswahl: lila Rahmen').toBe(purple);
      expect(state.bg, 'Auswahl: transluzente lila Füllung').toBe('rgba(147, 51, 234, 0.1)');
      expect(parseFloat(state.width), 'Auswahl: Rahmen sichtbar').toBeGreaterThanOrEqual(2);
    }
    expect(res.idle.border, 'Sekundärbutton: Rahmen purple-200').toBe('rgb(233, 213, 255)');
    expect(res.idle.color, 'Sekundärbutton: Text lila').toBe(purple);
    expect(res.filled.bg, 'gefüllter Primärbutton bleibt deckend gefüllt (Bootstrap-Active-Abdunkelung)').toBe('rgb(118, 41, 187)');
    expect(res.filled.color).toBe('rgb(255, 255, 255)');
    expect(res.success.bg, 'gefüllter Erfolgsbutton unverändert (Bootstrap-Active-Abdunkelung)').toBe('rgb(20, 108, 67)');
  });

  test('Profil: Google-Icon farbig, "Verknüpfen" lila/transluzent', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/user/linked-accounts*', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/user/passkeys*', (route) => route.fulfill({ json: [] }));
    await page.goto('/profile', { waitUntil: 'domcontentloaded' });
    const icon = page.locator('i.bi-google');
    await expect(icon).toBeVisible();
    expect(await icon.evaluate((el) => getComputedStyle(el).color), 'Google-Icon nicht schwarz/grau').toBe('rgb(220, 53, 69)');
    const link = page.getByRole('button', { name: 'Verknüpfen' });
    await expect(link).toBeVisible();
    expect(await link.evaluate((el) => getComputedStyle(el).color), 'Verknüpfen lila').toBe('rgb(147, 51, 234)');
    expect(await link.evaluate((el) => getComputedStyle(el).borderTopColor), 'Verknüpfen Rahmen purple-200').toBe('rgb(233, 213, 255)');
  });

  test('Freunde: Tab-Badges transluzent lila / weiß', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/friendships*', (route) => route.fulfill({ json: [] }));
    await page.goto('/friends', { waitUntil: 'domcontentloaded' });
    const badge = page.locator('[role=tab][aria-selected=true] .badge');
    await expect(badge).toBeVisible();
    expect(await badge.evaluate((el) => getComputedStyle(el).backgroundColor), 'aktiver Tab: transluzentes Weiß').toBe('rgba(255, 255, 255, 0.2)');
    const inactive = page.locator('[role=tab][aria-selected=false] .badge');
    if (await inactive.count()) {
      expect(await inactive.first().evaluate((el) => getComputedStyle(el).color)).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('Avatar-Menü zeigt den Dropdown-Pfeil (ab md, wie Nuxt "hidden md:block")', async ({ page, baseURL }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/appointments?**', (route) => route.fulfill({ json: { data: [], meta: { page: 0, size: 20, total: 0 } } }));
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.user-avatar').first()).toBeVisible();
    await expect(page.locator('button[aria-haspopup] i.bi-chevron-down')).toBeVisible();
  });

  test('Landing-Kopfzeile: Logo mit großer Rundung/Schatten, Anmelden mit lila Rahmen auf Weiß', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    const logo = page.locator('header img[alt="Chronos Logo"]');
    await expect(logo).toBeVisible();
    expect(parseFloat(await logo.evaluate((el) => getComputedStyle(el).borderTopLeftRadius)), 'Logo-Rundung').toBeGreaterThanOrEqual(12);
    expect(await logo.evaluate((el) => getComputedStyle(el).boxShadow), 'Logo-Schatten').not.toBe('none');
    const login = page.getByRole('link', { name: 'Anmelden' }).first();
    expect(await login.evaluate((el) => getComputedStyle(el).borderTopColor), 'Anmelden: lila Rahmen').toBe('rgb(147, 51, 234)');
    expect(await login.evaluate((el) => getComputedStyle(el).backgroundColor), 'Anmelden: weiß').toBe('rgb(255, 255, 255)');
  });
});
