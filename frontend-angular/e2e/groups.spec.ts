import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

test.use({ serviceWorkers: 'block' });

test.describe('Gruppen', () => {
  test('legt eine Gruppe an und zeigt sie in der Liste', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    const groups: object[] = [];
    let created: unknown;
    await page.route('**/api/v2/groups', (route) => {
      if (route.request().method() === 'POST') {
        created = route.request().postDataJSON();
        const group = { id: 5, name: (created as { name: string }).name, members: [] };
        groups.push(group);
        return route.fulfill({ json: group });
      }
      return route.fulfill({ json: groups });
    });

    await page.goto('/groups', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Noch keine Gruppen')).toBeVisible();

    await page.getByRole('button', { name: 'Neue Gruppe' }).first().click();
    await page.getByPlaceholder('z.B. Leiterrunde, Helfer-Team...').fill('Leiterrunde');
    await page.getByRole('button', { name: 'Erstellen', exact: true }).click();

    expect(created).toEqual({ name: 'Leiterrunde' });
    await expect(page.getByRole('heading', { name: 'Leiterrunde' })).toBeVisible();
  });
});
