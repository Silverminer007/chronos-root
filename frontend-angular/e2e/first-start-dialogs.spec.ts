import { expect, test } from '@playwright/test';
import { mockSession } from './support/mock-session';

// Reproduktion #208: Beim Erststart erscheinen „Neu in Chronos“ und die Push-Abfrage gleichzeitig. Der Neuigkeiten-Dialog
// liegt oben und fängt die Klicks auf „Nicht jetzt“ ab. Die Dialoge müssen nacheinander erscheinen (oder korrekt
// gestapelt sein), und jeder muss bedienbar sein.
test.use({ serviceWorkers: 'block' });

test.describe('Erststart-Dialoge (#208)', () => {
  test('„Nicht jetzt“ im Push-Dialog ist trotz Neuigkeiten-Dialog bedienbar', async ({ page, baseURL }) => {
    await mockSession(page, baseURL ?? '');
    // Erststart: Neuigkeiten und Push-Frage sind noch nicht beantwortet (überschreibt die Unterdrückung des Mocks).
    await page.addInitScript(() => {
      localStorage.removeItem('shownFeatureIds');
      localStorage.removeItem('pushPromptAnswer');
      localStorage.removeItem('pushPromptShownAt');
    });
    // Headless-Chromium hat eine Push-Berechtigung „default“; die Abfrage soll deterministisch erscheinen.
    await page.addInitScript(() => {
      Object.defineProperty(Notification, 'permission', { get: () => 'default' });
    });
    await page.route('**/api/v2/appointments?**', (route) =>
      route.fulfill({ json: { data: [], meta: { page: 0, size: 20, total: 0 } } }),
    );
    await page.goto('/agenda', { waitUntil: 'domcontentloaded' });

    const pushDialog = page.getByRole('dialog', { name: 'Push-Benachrichtigungen aktivieren?' });
    await expect(pushDialog).toBeVisible();

    // Schlägt fehl, solange app-new-features-dialog die Pointer-Events abfängt.
    await pushDialog.getByRole('button', { name: 'Nicht jetzt' }).click({ timeout: 3000 });
    await expect(pushDialog).toBeHidden();

    const featuresDialog = page.getByRole('dialog', { name: /./ }).filter({ hasText: 'Neu in Chronos' });
    await expect(featuresDialog).toBeVisible();
    await featuresDialog.getByRole('button', { name: 'Alle überspringen' }).click({ timeout: 3000 });
    await expect(featuresDialog).toBeHidden();
  });
});
