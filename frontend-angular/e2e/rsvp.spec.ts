import { expect, Page, test } from '@playwright/test';
import { mockSession, user } from './support/mock-session';

// Reproduktion #206: Zusagen/Absagen muss `POST /api/v2/appointments/{id}/participation` mit `{"status": ...}` aufrufen.
// Der gemockte BFF verhält sich wie das echte Backend: nur diese Route antwortet mit 200, alles andere (z. B.
// `/participants/approve`, dort als userId ausgewertet) mit 400 „Ungültiger Request-Body“.
test.use({ serviceWorkers: 'block' });

const base = {
  id: 1,
  name: 'Gruppenstunde',
  description: null,
  start: '2026-10-21T16:00:00Z',
  end: '2026-10-21T19:00:00Z',
  venue: null,
  status: 'PLANNED',
  minimal_attendees: 0,
  group_participants: [],
  messages: [],
};

const own = (status: string) => ({
  user_id: user.id,
  name: 'Max Muster',
  profile_picture_url: '',
  role: 'ATTENDANT',
  status,
  via_group_id: null,
  via_group_name: null,
});

async function mockBackend(page: Page, baseUrl: string, posts: string[]): Promise<void> {
  await mockSession(page, baseUrl);
  let status = 'PENDING';
  const current = () => ({ ...base, participants: [own(status)] });
  await page.route('**/api/v2/appointments?**', (route) =>
    route.fulfill({ json: { data: [current()], meta: { page: 0, size: 20, total: 1 } } }),
  );
  await page.route('**/api/v2/appointments/1?**', (route) => route.fulfill({ json: current() }));
  await page.route('**/api/v2/appointments/1/**', (route) => {
    const request = route.request();
    if (request.method() !== 'POST') return route.fallback();
    const path = new URL(request.url()).pathname;
    posts.push(`${path} ${request.postData() ?? ''}`);
    const body = request.postDataJSON() as { status?: string } | null;
    if (path === '/api/v2/appointments/1/participation' && (body?.status === 'APPROVED' || body?.status === 'REJECTED')) {
      status = body.status;
      return route.fulfill({ status: 200, json: {} });
    }
    return route.fulfill({ status: 400, json: { message: 'Ungültiger Request-Body' } });
  });
}

test.describe('Zusagen/Absagen auf der Agenda-Karte (#206)', () => {
  test('Zusagen sendet POST /participation {"status":"APPROVED"} und zeigt den Toast', async ({ page, baseURL }) => {
    const posts: string[] = [];
    await mockBackend(page, baseURL ?? '', posts);
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: 'Zusagen' }).click();

    await expect(page.getByText('Zusage bestätigt')).toBeVisible();
    expect(posts).toEqual(['/api/v2/appointments/1/participation {"status":"APPROVED"}']);
  });

  test('Absagen sendet POST /participation {"status":"REJECTED"} und zeigt den Toast', async ({ page, baseURL }) => {
    const posts: string[] = [];
    await mockBackend(page, baseURL ?? '', posts);
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: 'Absagen' }).click();

    await expect(page.getByText('Absage registriert')).toBeVisible();
    expect(posts).toEqual(['/api/v2/appointments/1/participation {"status":"REJECTED"}']);
  });
});
