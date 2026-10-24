import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #248: Auf Handybreite füllen Zusagen/Nachricht/Absagen auf der Agenda-Karte die Kartenbreite zu
// gleichen Teilen (Nuxt: `flex-1 sm:flex-initial`). Angular zeigt kleine, linksbündige Icon-Buttons.
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

test.describe('Aktions-Buttons der Agenda-Karte bei 390 px (#248)', () => {
  test('die drei Buttons verteilen sich gleichmäßig über die ganze Breite der Aktionszeile', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/appointments?**', (route) =>
      route.fulfill({
        json: {
          data: [
            {
              id: 1,
              name: 'Gruppenstunde',
              description: null,
              start: '2026-10-21T16:00:00Z',
              end: '2026-10-21T19:00:00Z',
              venue: null,
              status: 'PLANNED',
              minimal_attendees: 0,
              participants: [],
              group_participants: [],
              messages: [],
            },
          ],
          meta: { page: 0, size: 20, total: 1 },
        },
      }),
    );
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });

    const actions = page.locator('.appointment-card .appointment-actions').first();
    const buttons = actions.locator('button');
    await expect(buttons).toHaveCount(3);

    const row = (await actions.boundingBox())!;
    const boxes = await Promise.all([0, 1, 2].map(async (i) => (await buttons.nth(i).boundingBox())!));
    const covered = boxes[2].x + boxes[2].width - boxes[0].x;
    const widths = boxes.map((b) => Math.round(b.width));

    expect(covered, `Buttons belegen ${Math.round(covered)} px von ${Math.round(row.width)} px (Breiten ${widths.join('/')})`).toBeGreaterThanOrEqual(
      row.width - 2,
    );
    expect(Math.max(...widths) - Math.min(...widths), `Button-Breiten ${widths.join('/')} sind nicht gleich`).toBeLessThanOrEqual(2);
  });
});
