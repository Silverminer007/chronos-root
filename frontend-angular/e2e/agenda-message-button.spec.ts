import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #209: Die Agenda-Karte braucht neben Zusagen/Absagen den Nachricht-Button (Papierflieger), der den
// MessageDialog des Termins der Karte öffnet.
test.use({ serviceWorkers: 'block' });

const appointment = (id: number, name: string) => ({
  id,
  name,
  description: null,
  start: '2026-10-21T16:00:00Z',
  end: '2026-10-21T19:00:00Z',
  venue: null,
  status: 'PLANNED',
  minimal_attendees: 0,
  participants: [],
  group_participants: [],
  messages: [],
});

test.describe('Nachricht senden auf der Agenda-Karte (#209)', () => {
  test('Karte zeigt den Nachricht-Button, der den Dialog des Termins öffnet und die Nachricht sendet', async ({ page, baseURL }) => {
    const posts: string[] = [];
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/appointments?**', (route) =>
      route.fulfill({
        json: { data: [appointment(1, 'Gruppenstunde'), appointment(2, 'Zeltlager')], meta: { page: 0, size: 20, total: 2 } },
      }),
    );
    await page.route('**/api/v2/appointments/2/messages', (route) => {
      posts.push(route.request().postData() ?? '');
      return route.fulfill({ status: 200, json: {} });
    });
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });

    const card = page.locator('.appointment-card', { has: page.getByRole('heading', { name: 'Zeltlager' }) });
    await expect(card.getByRole('button', { name: 'Zusagen' })).toBeVisible();
    await card.getByRole('button', { name: /Nachricht/ }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('Zeltlager');
    await dialog.getByLabel('Nachricht', { exact: true }).fill('Hallo zusammen');
    await dialog.getByRole('button', { name: 'Nachricht senden' }).click();

    await expect.poll(() => posts).toEqual(['{"body":"Hallo zusammen"}']);
  });
});
