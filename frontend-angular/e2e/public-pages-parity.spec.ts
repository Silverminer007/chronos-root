import { expect, test } from '@playwright/test';

// Reproduktion #235: Öffentliche Seiten weichen vom alten Frontend ab ('Anmelden'-Button, Icons in Überschriften,
// Überschriftenfarbe, Kartenrundung/-innenabstand). Sollwerte: berechnete Werte des alten Frontends.
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

const GRAY_800 = 'rgb(31, 41, 55)';
const PAGES = ['/public/impressum', '/public/datenschutz'];

test("Kopfzeile: 'Anmelden' ist transparent mit weißem 2-px-Rahmen und weißer Schrift", async ({ page }) => {
  await page.goto('/public/impressum', { waitUntil: 'domcontentloaded' });
  const login = page.locator('header a[href="/api/auth/login"]');
  await expect(login).toBeVisible();
  const s = await login.evaluate((el) => {
    const c = getComputedStyle(el);
    return { bg: c.backgroundColor, border: c.borderTopColor, width: c.borderTopWidth, color: c.color };
  });
  expect(s.bg, "'Anmelden' Hintergrund soll transparent sein").toBe('rgba(0, 0, 0, 0)');
  expect(s.border, "'Anmelden' Rahmenfarbe soll weiß sein").toBe('rgb(255, 255, 255)');
  expect(s.width, "'Anmelden' Rahmenbreite").toBe('2px');
  expect(s.color, "'Anmelden' Schriftfarbe soll weiß sein").toBe('rgb(255, 255, 255)');
});

for (const path of PAGES) {
  test(`${path}: Icons in Überschriften, Überschriftenfarbe, kein Abschneiden`, async ({ page }) => {
    await page.goto(path, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.glass-card h2').first()).toBeVisible();
    const res = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>('.glass-card h2')].map((h) => ({
        text: h.textContent!.trim(),
        icons: h.querySelectorAll('i.bi, svg, .bi').length,
        color: getComputedStyle(h).color,
        clipped: h.scrollWidth > h.clientWidth,
      })),
    );
    expect(res.length, 'Abschnittsüberschriften gefunden').toBeGreaterThan(0);
    for (const h of res) {
      expect(h.icons, `Überschrift "${h.text}" hat kein Icon`).toBeGreaterThan(0);
      expect(h.color, `Überschrift "${h.text}" soll gray-800 sein`).toBe(GRAY_800);
      expect(h.clipped, `Überschrift "${h.text}" ist abgeschnitten`).toBe(false);
    }
  });

  test(`${path}: Karte mit 16 px Rundung und 24 px Innenabstand`, async ({ page }) => {
    await page.goto(path, { waitUntil: 'domcontentloaded' });
    const card = page.locator('.glass-card').first();
    await expect(card).toBeVisible();
    const s = await card.evaluate((el) => {
      const c = getComputedStyle(el);
      return { radius: c.borderTopLeftRadius, pad: c.paddingTop };
    });
    expect(s.radius, 'Kartenrundung').toBe('16px');
    expect(s.pad, 'Karteninnenabstand').toBe('24px');
  });
}
