import { expect, test } from '@playwright/test';

// Reproduktion #233: Abschnittsüberschriften öffentlicher Seiten werden auf 390 px mit "…" abgeschnitten statt umzubrechen.
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

for (const path of ['/public/impressum', '/public/datenschutz', '/public/install']) {
  test(`${path} bei 390px: Überschriften vollständig sichtbar, ohne Ellipsis`, async ({ page }) => {
    await page.goto(path, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('main h2, h2').first()).toBeVisible();
    const res = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>('h2, h3')].map((h) => ({
        text: h.textContent!.trim(),
        clipped: h.scrollWidth > h.clientWidth,
        ellipsis: getComputedStyle(h).textOverflow === 'ellipsis',
      })),
    );
    expect(res.length, 'Überschriften gefunden').toBeGreaterThan(0);
    for (const h of res) {
      expect(h.clipped, `Überschrift "${h.text}" ist abgeschnitten (scrollWidth > clientWidth)`).toBe(false);
      expect(h.ellipsis, `Überschrift "${h.text}" hat text-overflow: ellipsis`).toBe(false);
    }
  });
}
