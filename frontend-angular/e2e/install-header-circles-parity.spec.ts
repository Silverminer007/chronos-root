import { expect, test, type Page } from '@playwright/test';

// Reproduktion #241: Schritt-Ziffernkreise der Install-Seite und 'Anmelden'-Button der Kopfzeile weichen vom alten
// Frontend ab. Sollwerte: frontend/app/pages/public/install.vue (bg-purple-100, text-purple-600) und
// frontend/app/layouts/landingpage.vue (bg-white, text-purple-600, border-2 border-purple-600, rounded-lg).
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

/** Normalisiert eine beliebige CSS-Farbe (auch oklch) über ein Canvas auf [r, g, b]. */
async function rgb(page: Page, selector: string, prop: 'color' | 'backgroundColor' | 'borderTopColor'): Promise<number[]> {
  return page.locator(selector).first().evaluate((el, p) => {
    const ctx = document.createElement('canvas').getContext('2d')!;
    ctx.fillStyle = getComputedStyle(el)[p as 'color'];
    ctx.fillRect(0, 0, 1, 1);
    return [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3);
  }, prop);
}

test('Schritt-Ziffernkreise: sehr helles Lila mit lila Zahl, keine dunkle Füllung', async ({ page }) => {
  await page.goto('/public/install', { waitUntil: 'domcontentloaded' });
  const circle = 'ol li > :first-child';
  await expect(page.locator(circle).first()).toBeVisible();
  const bg = await rgb(page, circle, 'backgroundColor');
  const fg = await rgb(page, circle, 'color');
  expect(Math.min(...bg), `Ziffernkreis-Hintergrund soll sehr hell sein (purple-100), ist rgb(${bg})`).toBeGreaterThanOrEqual(220);
  expect(fg, `Ziffernkreis-Zahl soll lila (nicht weiß) sein, ist rgb(${fg})`).not.toEqual([255, 255, 255]);
  expect(fg[0] > fg[1] && fg[2] > fg[1], `Zahl soll lila sein, ist rgb(${fg})`).toBe(true);
});

for (const path of ['/', '/public/install']) {
  test(`Kopfzeile ${path}: 'Anmelden' weiß gefüllt mit lila Schrift und lila Rahmen`, async ({ page }) => {
    await page.goto(path, { waitUntil: 'domcontentloaded' });
    const btn = 'header a[href="/api/auth/login"]';
    await expect(page.locator(btn)).toBeVisible();
    const bg = await rgb(page, btn, 'backgroundColor');
    const fg = await rgb(page, btn, 'color');
    expect(bg, `'Anmelden' Hintergrund soll weiß sein, ist rgb(${bg})`).toEqual([255, 255, 255]);
    expect(fg[0] > fg[1] && fg[2] > fg[1], `'Anmelden' Schrift soll lila sein, ist rgb(${fg})`).toBe(true);
  });
}
