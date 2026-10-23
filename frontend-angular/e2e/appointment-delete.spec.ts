import { expect, Page, test } from '@playwright/test';
import { mockSession, user } from './support/mock-session';

// Ticket #237: Organisatoren löschen einen Termin über „Termin löschen“ auf der Detailseite (mit Bestätigungsdialog).
test.use({ serviceWorkers: 'block' });

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
    { user_id: user.id, name: 'Max Muster', profile_picture_url: '', role: 'RESPONSIBLE', status: 'APPROVED', via_group_id: null, via_group_name: null },
  ],
};

async function mockBackend(page: Page, baseUrl: string, deletes: string[]): Promise<void> {
  await mockSession(page, baseUrl);
  await page.route('**/api/v2/appointments?**', (route) =>
    route.fulfill({ json: { data: [], meta: { page: 0, size: 20, total: 0 } } }),
  );
  await page.route('**/api/v2/appointments/1?**', (route) => route.fulfill({ json: appointment }));
  await page.route('**/api/v2/appointments/1', (route) => {
    const request = route.request();
    if (request.method() !== 'DELETE') return route.fallback();
    deletes.push(new URL(request.url()).pathname);
    return route.fulfill({ status: 200, json: {} });
  });
}

test.describe('Termin löschen (#237)', () => {
  test('Bestätigen sendet DELETE, zeigt den Toast und leitet auf /agenda', async ({ page, baseURL }) => {
    const deletes: string[] = [];
    await mockBackend(page, baseURL ?? '', deletes);
    await page.goto('/appointment/1', { waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: 'Termin löschen' }).click();
    await expect(page.getByText('Termin endgültig löschen? Das kann nicht rückgängig gemacht werden.')).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: 'Termin löschen' }).click();

    await expect(page.getByText('Termin gelöscht')).toBeVisible();
    await expect(page).toHaveURL(/\/agenda$/);
    expect(deletes).toEqual(['/api/v2/appointments/1']);
  });

  test('Abbrechen löst keinen Aufruf aus', async ({ page, baseURL }) => {
    const deletes: string[] = [];
    await mockBackend(page, baseURL ?? '', deletes);
    await page.goto('/appointment/1', { waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: 'Termin löschen' }).click();
    await page.getByRole('button', { name: 'Abbrechen' }).click();

    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(page).toHaveURL(/\/appointment\/1$/);
    expect(deletes).toEqual([]);
  });
});
