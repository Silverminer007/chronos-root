import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #249: Im Schritt „Teilnehmer“ des Anlege-Steppers steht nur die Überschrift „Mindestanzahl Teilnehmer“
// über „Ja“/„Nein“. Gefordert ist eine ausformulierte Frage („Soll es eine Mindestanzahl geben?“).
test.use({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });

test.describe('Mindestanzahl im Anlege-Stepper (#249)', () => {
  test('Schritt „Teilnehmer“ stellt die ausformulierte Frage „Soll es eine Mindestanzahl geben?“', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    await page.route('**/api/v2/appointments?**', (route) =>
      route.fulfill({ json: { data: [], meta: { page: 0, size: 20, total: 0 } } }),
    );
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Termin erstellen' }).click();

    const next = page.getByRole('button', { name: 'Weiter' });
    await page.getByLabel('Terminname').fill('Sommerfest');
    await next.click();
    await page.getByRole('button', { name: 'Heute' }).click();
    await next.click();
    await page.getByRole('button', { name: '1 Std', exact: true }).click();
    await next.click();
    await next.click(); // Beschreibung überspringen
    await page.getByLabel('Ort').fill('Pfarrheim');
    await next.click();

    await expect(page.getByRole('heading', { name: 'Teilnehmer' })).toBeVisible();
    await expect(
      page.getByText('Soll es eine Mindestanzahl geben?'),
      'Frage nach der Mindestanzahl fehlt: nur die Überschrift „Mindestanzahl Teilnehmer“ über Ja/Nein',
    ).toBeVisible({ timeout: 3000 });
  });
});
