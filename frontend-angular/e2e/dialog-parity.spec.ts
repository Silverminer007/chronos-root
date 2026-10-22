import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #222: Dialoge und Anlegen-Stepper weichen vom alten Frontend ab. Titel nicht fett (alt 700),
// Footer-Buttons nebeneinander statt untereinander in voller Breite, Platzhalter im Stepper dunkelgrau statt gray-300.
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

test.describe('Dialoge und Stepper wie im alten Frontend (#222)', () => {
  test('Dialog „Freund hinzufügen“: Titel fett, Footer-Buttons untereinander in voller Breite', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/friendships/friends', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests/incoming', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests/outgoing', (route) => route.fulfill({ json: [] }));
    await page.goto('/friends', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Hinzufügen' }).first().click();

    const dialog = page.getByRole('dialog', { name: 'Freund hinzufügen' });
    await expect(dialog).toBeVisible();
    const res = await dialog.evaluate((el) => {
      const css = (e: Element) => getComputedStyle(e);
      const footer = el.querySelector('.modal-footer')!;
      const buttons = [...footer.querySelectorAll('button')].map((b) => b.getBoundingClientRect());
      return {
        inputHeight: el.querySelector('.modal-body .form-control')!.getBoundingClientRect().height,
        inputBorder: css(el.querySelector('.modal-body .form-control')!).borderTopWidth,
        weight: css(el.querySelector('.modal-title')!).fontWeight,
        direction: css(footer).flexDirection,
        footerWidth: footer.getBoundingClientRect().width,
        buttons: buttons.map((r) => ({ width: r.width, top: r.top })),
      };
    });

    expect(res.weight, 'Schriftstärke des Dialogtitels (erwartet 700)').toBe('700');
    expect(res.inputHeight, 'Höhe des Eingabefelds (erwartet ca. 52 px)').toBeGreaterThanOrEqual(52);
    expect(res.inputBorder, 'Rahmen des Eingabefelds (erwartet 2 px)').toBe('2px');
    expect(res.direction, 'Anordnung der Footer-Buttons (erwartet column)').toBe('column');
    expect(res.buttons.length).toBeGreaterThanOrEqual(2);
    for (const b of res.buttons) {
      expect(b.width, 'Breite der Footer-Buttons (erwartet volle Footer-Breite)').toBeGreaterThan(res.footerWidth * 0.8);
    }
  });

  test('Stepper „Termin anlegen“: Platzhalter des Namensfelds hellgrau (gray-300)', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/appointments?**', (route) =>
      route.fulfill({ json: { data: [], meta: { page: 0, size: 20, total: 0 } } }),
    );
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Termin erstellen' }).click();

    const input = page.getByLabel('Terminname');
    await expect(input).toBeVisible();
    const color = await input.evaluate((el) => getComputedStyle(el, '::placeholder').color);
    expect(color, 'Platzhalterfarbe im Stepper (erwartet gray-300)').toBe('rgb(209, 213, 219)');
    const line = await input.evaluate((el) => {
      const c = getComputedStyle(el);
      return { color: c.borderBottomColor, width: parseFloat(c.borderBottomWidth) };
    });
    expect(line.color, 'Unterstrich des Namensfelds (erwartet lila)').toBe('rgb(147, 51, 234)');
    expect(line.width, 'Dicke des Unterstrichs (erwartet > 2 px)').toBeGreaterThan(2);
  });
});
