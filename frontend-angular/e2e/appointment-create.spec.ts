import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

test.use({ serviceWorkers: 'block' });

test.describe('Termin anlegen', () => {
  test('führt durch den Stepper und sendet den Termin ans Backend', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    let payload: Record<string, unknown> | undefined;
    await page.route('**/api/v2/appointments?**', (route) =>
      route.fulfill({ json: { data: [], meta: { page: 0, size: 20, total: 0 } } }),
    );
    await page.route('**/api/v2/appointments', (route) => {
      payload = route.request().postDataJSON();
      return route.fulfill({ json: { id: 42, ...payload, status: 'PLANNED', participants: [], group_participants: [], messages: [] } });
    });
    await page.route('**/api/v2/appointments/42**', (route) =>
      route.fulfill({
        json: {
          id: 42,
          name: 'Sommerfest',
          description: null,
          start: '2026-10-21T16:00:00Z',
          end: '2026-10-21T17:00:00Z',
          venue: 'Pfarrheim',
          status: 'PLANNED',
          minimal_attendees: 0,
          participants: [],
          group_participants: [],
          messages: [],
        },
      }),
    );

    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Termin erstellen' }).click();

    const next = page.getByRole('button', { name: 'Weiter' });
    await expect(next).toBeDisabled();
    await page.getByLabel('Terminname').fill('Sommerfest');
    await next.click();
    await page.getByRole('button', { name: 'Heute' }).click();
    await next.click();
    await page.getByRole('button', { name: '1 Std', exact: true }).click();
    await next.click();
    await next.click(); // Beschreibung überspringen
    await page.getByLabel('Ort').fill('Pfarrheim');
    await next.click();
    await page.getByRole('button', { name: 'Erstellen', exact: true }).click();

    await expect(page).toHaveURL(/\/appointment\/42$/);
    expect(payload).toMatchObject({ name: 'Sommerfest', venue: 'Pfarrheim', description: null, minimal_attendees: 0 });
    const minutes = (Date.parse(payload?.['end'] as string) - Date.parse(payload?.['start'] as string)) / 60_000;
    expect(minutes).toBe(60);
  });
});
