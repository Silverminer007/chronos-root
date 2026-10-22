import { expect, Page, test } from '@playwright/test';

// Die Agenda läuft gegen den echten BFF (Hosting, Cookie-Weiterleitung); Sitzung, Benutzer und Termine kommen aus
// gemockten API-Antworten. Der Service Worker bleibt aus, damit `page.route` alle Anfragen sieht.
test.use({ serviceWorkers: 'block' });

const appointment = (id: number, name: string, extra: object = {}) => ({
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
  ...extra,
});

const user = { id: 1, first_name: 'Max', last_name: 'Muster', email: 'max@example.org' };
const page1 = [appointment(1, 'Gruppenstunde', { venue: 'Pfarrheim' }), appointment(2, 'Zeltlager')];

async function mockBff(page: Page, baseUrl: string, requests: string[] = []): Promise<void> {
  await page.context().addCookies(
    ['kc_access', 'kc_refresh', 'kc_expires'].map((name) => ({
      name,
      value: name === 'kc_expires' ? String(Math.floor(Date.now() / 1000) + 3600) : 'e2e',
      url: baseUrl,
    })),
  );
  // Onboarding, Neuigkeiten und Push-Dialog würden die Seite sonst überdecken.
  await page.addInitScript(() => {
    localStorage.setItem('onboardingShownAt', '1');
    localStorage.setItem('shownFeatureIds', JSON.stringify(['profile-page-v1', 'linked-accounts-v1', 'passkeys-v1', 'change-password-v1']));
    localStorage.setItem('pushPromptAnswer', 'denied');
  });
  await page.route('**/api/auth/isLoggedIn', (route) => route.fulfill({ status: 204 }));
  await page.route('**/api/v2/user', (route) => route.fulfill({ json: user }));
  await page.route('**/api/v2/appointments?**', (route) => {
    const url = new URL(route.request().url());
    requests.push(url.search);
    const search = url.searchParams.get('search');
    const pageNumber = Number(url.searchParams.get('page'));
    if (search) {
      const hits = page1.filter((a) => a.name.toLowerCase().includes(search.toLowerCase()));
      return route.fulfill({ json: { data: hits, meta: { page: 0, size: 20, total: hits.length } } });
    }
    return route.fulfill({
      json:
        pageNumber === 0
          ? { data: page1, meta: { page: 0, size: 20, total: 21 } }
          : { data: [appointment(3, 'Sommerfest')], meta: { page: 1, size: 20, total: 21 } },
    });
  });
}

test.describe('Agenda', () => {
  test('zeigt die Termine der ersten Seite', async ({ page, baseURL }) => {
    await mockBff(page, baseURL ?? '');
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('heading', { name: 'Gruppenstunde' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Zeltlager' })).toBeVisible();
    await expect(page.getByText('Pfarrheim')).toBeVisible();
    await expect(page.getByText('Mi., 21.10.2026').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Mehr laden' })).toBeVisible();
  });

  test('„Mehr laden“ hängt die nächste Seite an', async ({ page, baseURL }) => {
    await mockBff(page, baseURL ?? '');
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Mehr laden' }).click();

    await expect(page.getByRole('heading', { name: 'Sommerfest' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Gruppenstunde' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Mehr laden' })).toHaveCount(0);
  });

  test('die Suche filtert die Liste über das Backend', async ({ page, baseURL }) => {
    const requests: string[] = [];
    await mockBff(page, baseURL ?? '', requests);
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Gruppenstunde' })).toBeVisible();

    await page.getByPlaceholder('Termine durchsuchen...').fill('zelt');

    await expect(page.getByRole('heading', { name: 'Zeltlager' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Gruppenstunde' })).toHaveCount(0);
    expect(requests.some((r) => r.includes('search=zelt'))).toBe(true);
  });

  test('zeigt den Leerzustand, wenn nichts gefunden wird', async ({ page, baseURL }) => {
    await mockBff(page, baseURL ?? '');
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await page.getByPlaceholder('Termine durchsuchen...').fill('gibtesnicht');

    await expect(page.getByText('Keine Termine vorhanden')).toBeVisible();
  });

  test('Zusagen schickt die Zusage ab und zeigt einen Toast', async ({ page, baseURL }) => {
    await mockBff(page, baseURL ?? '');
    let approved = false;
    await page.route('**/api/v2/appointments/1/participants/approve', (route) => {
      approved = true;
      return route.fulfill({ status: 204 });
    });
    await page.route('**/api/v2/appointments/1?**', (route) =>
      route.fulfill({
        json: appointment(1, 'Gruppenstunde', {
          participants: [{ user_id: 1, name: 'Max Muster', role: 'ATTENDANT', status: 'APPROVED' }],
        }),
      }),
    );
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Zusagen' }).first().click();

    await expect(page.getByText('Zusage bestätigt')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Zusagen' }).first()).toBeDisabled();
    expect(approved).toBe(true);
  });

  test('passt auf 360 px Breite ohne horizontales Scrollen', async ({ page, baseURL }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await mockBff(page, baseURL ?? '');
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Zeltlager' })).toBeVisible();

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
});
