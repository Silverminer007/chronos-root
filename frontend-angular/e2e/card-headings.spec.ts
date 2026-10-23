import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #219: Kartenüberschriften sind 20 px statt 18 px groß (Bootstrap `.h5`), Wörter brechen auf 390 px
// mitten im Wort um.
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

test.describe('Kartenüberschriften (#219)', () => {
  test('Einstellungen bei 390px: Kartenüberschriften 18 px, kein Umbruch mitten im Wort', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/settings', (route) => route.fulfill({ status: 404, json: {} }));
    await page.goto('/settings', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Terminbenachrichtigungen' })).toBeVisible();
    const res = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>('.card h2, .card h3, .card h4, .card h5')].map((h) => {
        // Wörter einzeln messen: ein Wort, das über mehrere Zeilen läuft, wurde mitten im Wort umgebrochen.
        const broken: string[] = [];
        const walker = document.createTreeWalker(h, NodeFilter.SHOW_TEXT);
        for (let n = walker.nextNode(); n; n = walker.nextNode()) {
          for (const m of (n.textContent ?? '').matchAll(/[^\s-]+/g)) {
            const r = document.createRange();
            r.setStart(n, m.index!);
            r.setEnd(n, m.index! + m[0].length);
            if (r.getClientRects().length > 1) broken.push(m[0]);
          }
        }
        return { text: h.textContent!.trim(), size: getComputedStyle(h).fontSize, broken };
      }),
    );
    expect(res.length, 'Überschriften gefunden').toBeGreaterThanOrEqual(3);
    for (const h of res) {
      expect(h.size, `Schriftgröße von "${h.text}" (erwartet 18px)`).toBe('18px');
      expect(h.broken, `Wörter in "${h.text}" brechen mitten im Wort um`).toEqual([]);
    }
  });
});
