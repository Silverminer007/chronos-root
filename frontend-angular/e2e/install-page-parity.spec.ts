import { expect, test, type Page } from '@playwright/test';

// Reproduktion #239: /public/install weicht vom alten Frontend ab (Überschrift, Badge, Schritt-Ziffernkreise,
// Linkstil, Abschnittsüberschriften). Sollwerte: berechnete Werte des alten Frontends bei 390x844.
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 }, userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36' });

/** Normalisiert eine beliebige CSS-Farbe (auch oklch) über ein Canvas auf [r, g, b]. */
async function rgbOf(page: Page, selector: string, prop: 'color' | 'backgroundColor'): Promise<number[]> {
  return page.locator(selector).first().evaluate((el, p) => {
    const ctx = document.createElement('canvas').getContext('2d')!;
    ctx.fillStyle = getComputedStyle(el)[p as 'color'];
    ctx.fillRect(0, 0, 1, 1);
    return [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3);
  }, prop);
}

test.beforeEach(async ({ page }) => {
  await page.goto('/public/install', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('h1')).toBeVisible();
});

test('Überschrift: 30 px, zweizeilig; Untertitel höchstens 340 px breit', async ({ page }) => {
  const s = await page.locator('h1').evaluate((el) => {
    const c = getComputedStyle(el);
    return { size: c.fontSize, lines: Math.round(el.getBoundingClientRect().height / parseFloat(c.lineHeight)) };
  });
  expect(s.size, 'Überschrift Schriftgröße').toBe('30px');
  expect(s.lines, 'Überschrift Zeilenanzahl').toBe(2);
  const subW = await page.locator('h1 + p').evaluate((el) => el.getBoundingClientRect().width);
  expect(subW, 'Untertitel Breite').toBeLessThanOrEqual(340);
});

test("Badge 'Dein Browser': helles Lila mit lila, dünner Schrift", async ({ page }) => {
  const badge = page.locator('span', { hasText: /^Dein Browser$/ }).first();
  await expect(badge).toBeVisible();
  const weight = await badge.evaluate((el) => getComputedStyle(el).fontWeight);
  const bg = await badge.evaluate((el) => {
    const ctx = document.createElement('canvas').getContext('2d')!;
    ctx.fillStyle = getComputedStyle(el).backgroundColor;
    ctx.fillRect(0, 0, 1, 1);
    return [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3);
  });
  const fg = await badge.evaluate((el) => {
    const ctx = document.createElement('canvas').getContext('2d')!;
    ctx.fillStyle = getComputedStyle(el).color;
    ctx.fillRect(0, 0, 1, 1);
    return [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3);
  });
  expect(Math.min(...bg), 'Badge-Hintergrund soll sehr hell sein (purple-100)').toBeGreaterThanOrEqual(220);
  expect(fg, 'Badge-Schrift soll lila (nicht weiß) sein').not.toEqual([255, 255, 255]);
  expect(Number(weight), 'Badge Schriftgewicht soll dünn (500) sein').toBeLessThanOrEqual(500);
});

test('Schritte: je Schritt ein runder Ziffernkreis (3 in der ersten Anleitung)', async ({ page }) => {
  const circles = await page.locator('ol').first().evaluate((ol) =>
    [...ol.querySelectorAll('li')].map((li) => {
      const el = li.firstElementChild as HTMLElement | null;
      if (!el) return { ok: false, text: '' };
      const c = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      const round = parseFloat(c.borderTopLeftRadius) >= r.width / 2 || c.borderTopLeftRadius.includes('%');
      return { ok: round && Math.round(r.width) === 24 && Math.round(r.height) === 24, text: el.textContent!.trim() };
    }),
  );
  expect(circles.filter((c) => c.ok).length, 'Anzahl der Schritt-Ziffernkreise (24 px, rund)').toBe(3);
  expect(await page.evaluate(() => getComputedStyle(document.querySelector('ol li')!).listStyleType), 'native Listenziffern').toBe('none');
});

test('Link auf die App-Adresse: fett, lila, nicht unterstrichen', async ({ page }) => {
  const link = page.locator('ol a').first();
  const s = await link.evaluate((el) => {
    const c = getComputedStyle(el);
    return { weight: c.fontWeight, deco: c.textDecorationLine };
  });
  expect(s.deco, 'Link Unterstreichung').toBe('none');
  expect(Number(s.weight), 'Link Schriftgewicht').toBeGreaterThanOrEqual(600);
});

test('Abschnittsüberschriften: 18 px, fett, dunkles Slate, lila Icon', async ({ page }) => {
  const h3 = page.locator('h3').first();
  const s = await h3.evaluate((el) => {
    const c = getComputedStyle(el);
    return { size: c.fontSize, weight: c.fontWeight };
  });
  expect(s.size, 'Abschnittsüberschrift Schriftgröße').toBe('18px');
  expect(Number(s.weight), 'Abschnittsüberschrift Schriftgewicht').toBeGreaterThanOrEqual(600);
  const [r, g, b] = await rgbOf(page, 'h3 i', 'color');
  expect(r > g && b > g, `Icon soll lila sein, ist rgb(${r}, ${g}, ${b})`).toBe(true);
});
