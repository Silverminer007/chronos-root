import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

test.use({ serviceWorkers: 'block' });

const friend = {
  user_id: 2,
  name: 'Lena Leiterin',
  email: 'lena@example.org',
  profile_picture_url: '',
  friends_since: '2026-01-15T10:00:00Z',
};

test.describe('Freunde', () => {
  test('listet Freunde auf und sendet eine Anfrage per E-Mail', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    const sent: string[] = [];
    await page.route('**/api/v2/friendships/friends', (route) => route.fulfill({ json: [friend] }));
    await page.route('**/api/v2/friendships/requests/incoming', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests/outgoing', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests?**', (route) => {
      sent.push(new URL(route.request().url()).searchParams.get('email') ?? '');
      return route.fulfill({ status: 204 });
    });

    await page.goto('/friends', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Lena Leiterin')).toBeVisible();
    await expect(page.getByText('lena@example.org')).toBeVisible();

    await page.getByRole('button', { name: 'Hinzufügen' }).click();
    await page.getByPlaceholder('freund@beispiel.de').fill('neu@example.org');
    await page.getByRole('button', { name: 'Senden' }).click();

    await expect.poll(() => sent).toEqual(['neu@example.org']);
  });

  test('zeigt eingehende Anfragen im Tab „Anfragen“', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/friendships/friends', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests/outgoing', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/v2/friendships/requests/incoming', (route) =>
      route.fulfill({
        json: [
          {
            requestId: 7,
            userId: 3,
            userName: 'Paul Pfadfinder',
            userEmail: 'paul@example.org',
            profilePictureUrl: '',
            status: 'PENDING',
            createdAt: '2026-10-20T10:00:00Z',
            respondedAt: '',
            isIncoming: true,
          },
        ],
      }),
    );

    await page.goto('/friends', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Noch keine Freunde')).toBeVisible();
    await page.getByRole('tab', { name: /Anfragen/ }).click();

    await expect(page.getByText('Eingehende Anfragen')).toBeVisible();
    await expect(page.getByText('Paul Pfadfinder')).toBeVisible();
  });
});
