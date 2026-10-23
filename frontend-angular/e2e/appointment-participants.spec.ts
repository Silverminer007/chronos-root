import { expect, Page, test } from '@playwright/test';
import { mockSession, user } from './support/mock-session';

// Reproduktion #211: Teilnehmer hinzufügen/entfernen und Rolle ändern müssen die Routen des Backends aufrufen:
//   POST   /appointments/{id}/participants/{userId}      {"user_role": ...}
//   DELETE /appointments/{id}/participants/{userId}
//   PUT    /appointments/{id}/participation/{userId}/role {"role": ...}
// Der gemockte BFF verhält sich wie das Backend: nur diese Routen antworten mit 2xx, alles andere (z. B.
// `/participants/users`, dort als userId ausgewertet) mit 403 bzw. 400.
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

const participant = (id: number, name: string, role: string) => ({
  user_id: id,
  name,
  profile_picture_url: '',
  role,
  status: 'PENDING',
  via_group_id: null,
  via_group_name: null,
});

const owner = participant(user.id, 'Max Muster', 'RESPONSIBLE');
const lena = participant(2, 'Lena Leiterin', 'ATTENDANT');

async function mockBackend(page: Page, baseUrl: string, calls: string[], initial: ReturnType<typeof participant>[]): Promise<void> {
  await mockSession(page, baseUrl);
  const current = () => ({ ...base, participants: initial });
  await page.route('**/api/v2/appointments/1?**', (route) => route.fulfill({ json: current() }));
  await page.route('**/api/v2/friendships/friends', (route) =>
    route.fulfill({
      json: [{ user_id: 2, name: 'Lena Leiterin', email: 'lena@example.org', profile_picture_url: '', friends_since: '2026-01-15T10:00:00Z' }],
    }),
  );
  await page.route('**/api/v2/appointments/1/**', (route) => {
    const request = route.request();
    if (request.method() === 'GET') return route.fallback();
    const path = new URL(request.url()).pathname;
    calls.push(`${request.method()} ${path} ${request.postData() ?? ''}`.trim());
    const ok =
      (request.method() === 'POST' && path === '/api/v2/appointments/1/participants/2') ||
      (request.method() === 'DELETE' && path === '/api/v2/appointments/1/participants/2') ||
      (request.method() === 'PUT' && path === '/api/v2/appointments/1/participation/2/role');
    return ok ? route.fulfill({ status: 204 }) : route.fulfill({ status: 403, json: { message: 'Forbidden' } });
  });
}

test.describe('Teilnehmer eines Termins (#211)', () => {
  test('Teilnehmer hinzufügen sendet POST /participants/{userId} {"user_role":"ATTENDANT"}', async ({ page, baseURL }) => {
    const calls: string[] = [];
    await mockBackend(page, baseURL ?? '', calls, [owner]);
    await page.goto('/appointment/1', { waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: 'Teilnehmer hinzufügen' }).click();
    await page.getByPlaceholder('Name eingeben...').fill('Lena');
    await page.getByTestId('result').click();
    await page.getByRole('button', { name: 'Hinzufügen', exact: true }).click();

    await expect(page.getByText('Der Teilnehmer wurde erfolgreich hinzugefügt')).toBeVisible();
    expect(calls).toEqual(['POST /api/v2/appointments/1/participants/2 {"user_role":"ATTENDANT"}']);
  });

  test('Rolle ändern sendet PUT /participation/{userId}/role {"role":"HELPER"}', async ({ page, baseURL }) => {
    const calls: string[] = [];
    await mockBackend(page, baseURL ?? '', calls, [owner, lena]);
    await page.goto('/appointment/1', { waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: 'Aktionen für Lena Leiterin' }).click();
    await page.getByRole('menuitem', { name: 'Helfer' }).click();

    await expect(page.getByText('Die Teilnehmerrolle wurde erfolgreich geändert')).toBeVisible();
    expect(calls).toEqual(['PUT /api/v2/appointments/1/participation/2/role {"role":"HELPER"}']);
  });

  test('Teilnehmer entfernen sendet DELETE /participants/{userId}', async ({ page, baseURL }) => {
    const calls: string[] = [];
    await mockBackend(page, baseURL ?? '', calls, [owner, lena]);
    await page.goto('/appointment/1', { waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: 'Aktionen für Lena Leiterin' }).click();
    await page.getByRole('menuitem', { name: 'Entfernen' }).click();

    await expect(page.getByText('Lena Leiterin wurde entfernt')).toBeVisible();
    expect(calls).toEqual(['DELETE /api/v2/appointments/1/participants/2']);
  });
});
