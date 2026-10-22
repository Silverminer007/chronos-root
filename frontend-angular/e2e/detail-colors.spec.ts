import { expect, test } from '@playwright/test';
import { mockSession, user } from './support/mock-session';

// Reproduktion #221: Termindetail-Kopf hat dunklen Text (Bootstrap-Überschriftenfarbe) statt weiß, die Knöpfe
// „Link teilen“/„Termin bearbeiten“ sind deckend weiß (`btn-light`) statt halbtransparent (Nuxt `bg-white/20`) und die
// Icon-Kachel der Gruppendetailseite ist lila statt blau/indigo (Nuxt `from-blue-100 to-indigo-100`, Icon `text-blue-600`).
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

const appointment = {
  id: 1,
  name: 'Gruppenstunde',
  description: null,
  start: '2026-10-21T16:00:00Z',
  end: '2026-10-21T19:00:00Z',
  venue: 'Heim',
  status: 'PLANNED',
  minimal_attendees: 0,
  group_participants: [],
  messages: [],
  participants: [
    { user_id: user.id, name: 'Max Muster', profile_picture_url: '', role: 'RESPONSIBLE', status: 'PENDING', via_group_id: null, via_group_name: null },
  ],
};

const WHITE = 'rgb(255, 255, 255)';

test.describe('Farben der Detailseiten (#221)', () => {
  test('Termindetail: Kopftext weiß, Knöpfe halbtransparent mit weißen Icons', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/appointments/1?**', (route) => route.fulfill({ json: appointment }));
    await page.goto('/appointment/1', { waitUntil: 'domcontentloaded' });
    const title = page.locator('.bg-gradient-brand h2');
    await expect(title).toBeVisible();

    const res = await page.evaluate(() => {
      const css = (el: Element) => getComputedStyle(el);
      const header = document.querySelector('.bg-gradient-brand')!;
      const button = (label: string) => {
        const b = header.querySelector(`button[aria-label="${label}"]`)!;
        return { bg: css(b).backgroundColor, icon: css(b.querySelector('i')!).color };
      };
      return {
        title: css(header.querySelector('h2')!).color,
        date: css(header.querySelector('.bi-calendar-event')!.parentElement!).color,
        share: button('Link teilen'),
        edit: button('Termin bearbeiten'),
      };
    });

    expect(res.title, 'Textfarbe des Titels im Kopf (erwartet weiß)').toBe(WHITE);
    expect(res.date, 'Textfarbe des Datums im Kopf (erwartet weiß)').toBe(WHITE);
    for (const [name, b] of [['Link teilen', res.share], ['Termin bearbeiten', res.edit]] as const) {
      // halbtransparent weiß: rgba(255, 255, 255, a) mit 0 < a < 1
      expect(b.bg, `Hintergrund von "${name}" (erwartet halbtransparent weiß)`).toMatch(/^rgba\(255, 255, 255, 0?\.\d+\)$/);
      expect(b.icon, `Icon-Farbe von "${name}" (erwartet weiß)`).toBe(WHITE);
    }
  });

  test('Gruppendetail: Icon-Kachel blau/indigo statt lila', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/groups', (route) => route.fulfill({ json: [{ id: 1, name: 'Leiterrunde', members: [] }] }));
    await page.route('**/api/v2/groups/1/users', (route) => route.fulfill({ json: [user] }));
    await page.goto('/groups/1', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Leiterrunde' })).toBeVisible();

    const tile = await page.evaluate(() => {
      const el = document.querySelector('.icon-tile')!;
      return { bg: getComputedStyle(el).backgroundImage, icon: getComputedStyle(el).color };
    });
    // Nuxt: Verlauf blue-100 (#dbeafe) → indigo-100 (#e0e7ff), Icon blue-600 (#2563eb)
    expect(tile.bg, 'Verlauf der Icon-Kachel (erwartet blue-100 → indigo-100)').toContain('rgb(219, 234, 254)');
    expect(tile.bg, 'Verlauf der Icon-Kachel (erwartet blue-100 → indigo-100)').toContain('rgb(224, 231, 255)');
    expect(tile.icon, 'Icon-Farbe der Icon-Kachel (erwartet blue-600)').toBe('rgb(37, 99, 235)');

    const fab = await page.evaluate(() => {
      const el = document.querySelector('.fab')!;
      return { bg: getComputedStyle(el).backgroundImage };
    });
    expect(fab.bg, 'Verlauf des Plus-Buttons (erwartet blue-600 → indigo-500)').toContain('rgb(37, 99, 235)');
    expect(fab.bg, 'Verlauf des Plus-Buttons (erwartet blue-600 → indigo-500)').toContain('rgb(99, 102, 241)');
  });
});
