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
    expect(await css(page, '.appointment-card', 'background-color'), 'Termin-Karte weiß auf gray-50-Seite').toBe('rgb(255, 255, 255)');
    expect(await css(page, 'body', 'background-color'), 'Agenda: Seitenhintergrund gray-50').toBe('rgb(249, 250, 251)');
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

  // Konkrete Sollwerte (Nuxt): Karte weiß, Eingabe gray-50 in der weißen Karte, Seite gray-50, Gradient-Button weiße Schrift.
  const WHITE = 'rgb(255, 255, 255)';
  const GRAY_50 = 'rgb(249, 250, 251)';

  test('Profil: echte Eingabefelder gray-50 in weißer Karte mit Rundung >= 12', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/user/linked-accounts*', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/user/passkeys*', (route) => route.fulfill({ json: [] }));
    await page.goto('/profile', { waitUntil: 'domcontentloaded' });
    const input = page.locator('.card input.form-control').first();
    await expect(input).toBeVisible();
    expect(await css(page, 'body', 'background-color'), 'Profil: Seitenhintergrund').toBe(GRAY_50);
    expect(await css(page, '.card', 'background-color'), 'Profil: Karte weiß').toBe(WHITE);
    expect(await input.evaluate((el) => getComputedStyle(el).backgroundColor), 'Profil: Eingabe gray-50').toBe(GRAY_50);
    expect(await input.evaluate((el) => getComputedStyle(el).borderTopColor), 'Profil: Eingaberahmen gray-200').toBe('rgb(229, 231, 235)');
    expect(parseFloat(await input.evaluate((el) => getComputedStyle(el).borderTopLeftRadius)), 'Profil: Eingaberundung').toBeGreaterThanOrEqual(12);
    expect(parseFloat(await css(page, '.card', 'border-top-left-radius')), 'Profil: Kartenrundung').toBeGreaterThanOrEqual(16);
    expect(await css(page, '.card', 'box-shadow'), 'Profil: Kartenschatten').not.toBe('none');
  });

  test('Gruppen: weiße Karte mit Rundung/Schatten auf gray-50, Gradient-Button mit weißer Schrift', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/groups', (route) => route.fulfill({ json: [{ id: 5, name: 'Leiterrunde', members: [] }] }));
    await page.goto('/groups', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Leiterrunde').first()).toBeVisible();
    expect(await css(page, 'body', 'background-color'), 'Gruppen: Seitenhintergrund').toBe(GRAY_50);
    expect(await css(page, '.card', 'background-color'), 'Gruppen: Karte weiß').toBe(WHITE);
    expect(parseFloat(await css(page, '.card', 'border-top-left-radius')), 'Gruppen: Kartenrundung').toBeGreaterThanOrEqual(16);
    expect(await css(page, '.card', 'box-shadow'), 'Gruppen: Kartenschatten').not.toBe('none');
    const gradient = page.locator('.btn-gradient:visible').first();
    await expect(gradient).toBeVisible();
    expect(await gradient.evaluate((el) => getComputedStyle(el).color), 'btn-gradient: weiße Schrift').toBe(WHITE);
    expect(await gradient.evaluate((el) => getComputedStyle(el).backgroundImage), 'btn-gradient: Verlauf').toContain('linear-gradient');
  });

  test('Freunde: weiße Karte bzw. gray-50-Seite, Tab-Leiste mit Gradient', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/friendships*', (route) =>
      route.fulfill({ json: [{ id: 1, status: 'ACCEPTED', friend: { id: 2, first_name: 'Erika', last_name: 'Muster' } }] }),
    );
    await page.goto('/friends', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[role=tab]').first()).toBeVisible();
    expect(await css(page, 'body', 'background-color'), 'Freunde: Seitenhintergrund').toBe(GRAY_50);
    const card = page.locator('.card').first();
    if (await card.count()) {
      expect(await card.evaluate((el) => getComputedStyle(el).backgroundColor), 'Freunde: Karte weiß').toBe(WHITE);
      expect(parseFloat(await card.evaluate((el) => getComputedStyle(el).borderTopLeftRadius)), 'Freunde: Kartenrundung').toBeGreaterThanOrEqual(16);
    }
    expect(await css(page, '[role=tab][aria-selected=true]', 'color'), 'Freunde: aktiver Tab weiße Schrift').toBe(WHITE);
  });

  test('Einstellungen: weiße Karte auf gray-50, Schalter lila wenn aktiv, Speichern-Button weiße Schrift', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/settings', (route) => route.fulfill({ status: 404, json: {} }));
    await page.goto('/settings', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Terminbenachrichtigungen' })).toBeVisible();
    expect(await css(page, 'body', 'background-color'), 'Einstellungen: Seitenhintergrund').toBe(GRAY_50);
    expect(await css(page, '.card', 'background-color'), 'Einstellungen: Karte weiß').toBe(WHITE);
    expect(parseFloat(await css(page, '.card', 'border-top-left-radius')), 'Einstellungen: Kartenrundung').toBeGreaterThanOrEqual(16);
    expect(await css(page, '.card', 'box-shadow'), 'Einstellungen: Kartenschatten').not.toBe('none');
    expect(await css(page, 'h1', 'color'), 'Einstellungen: Überschrift gray-900').toBe('rgb(17, 24, 39)');
    const gradient = page.locator('.btn-gradient').first();
    if (await gradient.isVisible()) {
      expect(await gradient.evaluate((el) => getComputedStyle(el).color), 'Speichern: weiße Schrift').toBe(WHITE);
    }
  });

  test('Dunkelmodus: Eingabe-Rahmen adaptiv, Fokus- und Validierungsrahmen bleiben erhalten', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible();
    const res = await page.evaluate(() => {
      const border = (el: Element) => getComputedStyle(el).borderTopColor;
      const mk = (cls: string) => {
        const el = document.createElement('input');
        el.className = cls;
        el.style.transition = 'none';
        document.body.appendChild(el);
        return el;
      };
      const light = border(mk('form-control'));
      document.documentElement.setAttribute('data-bs-theme', 'dark');
      const dark = border(mk('form-control'));
      const invalid = border(mk('form-control is-invalid'));
      const focused = mk('form-control');
      focused.focus();
      return { light, dark, invalid, focus: border(focused), invalidRef: getComputedStyle(document.documentElement).getPropertyValue('--bs-form-invalid-border-color').trim() };
    });
    expect(res.light, 'hell: gray-200').toBe('rgb(229, 231, 235)');
    expect(res.dark, 'dunkel: nicht das helle gray-200').not.toBe(res.light);
    expect(res.invalid, 'dunkel: Validierungsrahmen bleibt').not.toBe(res.dark);
    expect(res.focus, 'dunkel: Fokusrahmen bleibt').not.toBe(res.dark);
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

  test('Einstellungen bei 390px: Push-Schalter und Überschrift laufen nicht über', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/settings', (route) => route.fulfill({ status: 404, json: {} }));
    await page.goto('/settings', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Terminbenachrichtigungen' })).toBeVisible();
    const sw = page.getByRole('switch', { name: 'Push-Benachrichtigungen' });
    await expect(sw).toBeVisible();
    const card = page.locator('.card', { has: sw });
    const [swBox, cardBox] = await Promise.all([sw.boundingBox(), card.boundingBox()]);
    expect(swBox!.x + swBox!.width, 'Schalter innerhalb der Karte').toBeLessThanOrEqual(cardBox!.x + cardBox!.width);
    const overflow = await page.evaluate(() => ({
      doc: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      cards: [...document.querySelectorAll<HTMLElement>('.card, .card-header, .card-body')].filter((e) => e.scrollWidth > e.clientWidth).length,
      heading: [...document.querySelectorAll<HTMLElement>('h3')].filter((e) => e.scrollWidth > e.clientWidth).length,
    }));
    expect(overflow.doc, 'Seite ohne horizontalen Überlauf').toBeLessThanOrEqual(0);
    expect(overflow.cards, 'Karten: scrollWidth <= clientWidth').toBe(0);
    expect(overflow.heading, 'Überschriften: scrollWidth <= clientWidth').toBe(0);
  });

  // #219: Header-Logo wie Nuxt (`w-8 h-8 sm:w-10 sm:h-10`), ohne weißen Kasten und ohne Schatten.
  for (const { width, size } of [{ width: 390, size: '32px' }, { width: 640, size: '40px' }]) {
    test(`Header-Logo bei ${width}px: ${size} groß, ohne Kasten und Schatten`, async ({ page, baseURL }) => {
      await page.setViewportSize({ width, height: 844 });
      await mockSession(page, baseURL ?? '');
      await page.route('**/api/v2/appointments?**', (route) =>
        route.fulfill({ json: { data: [], meta: { page: 0, size: 20, total: 0 } } }),
      );
      await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
      const logo = page.locator('header img.header-logo').first();
      await expect(logo).toBeVisible();
      const s = await logo.evaluate((el) => {
        const c = getComputedStyle(el);
        return { w: c.width, h: c.height, bg: c.backgroundColor, shadow: c.boxShadow, padding: c.padding, border: c.borderTopWidth };
      });
      expect(s.w, 'Logo-Breite').toBe(size);
      expect(s.h, 'Logo-Höhe').toBe(size);
      expect(s.bg, 'Logo ohne Hintergrund (kein weißer Kasten)').toBe('rgba(0, 0, 0, 0)');
      expect(s.shadow, 'Logo ohne Schatten').toBe('none');
      expect(s.padding, 'Logo ohne Innenabstand').toBe('0px');
      expect(s.border, 'Logo ohne Rahmen').toBe('0px');
    });
  }
});
