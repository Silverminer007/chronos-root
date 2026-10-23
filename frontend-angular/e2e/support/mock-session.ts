import { Page } from '@playwright/test';

export const user = { id: 1, first_name: 'Max', last_name: 'Muster', email: 'max@example.org' };

/**
 * Gemockte Sitzung für Tests gegen den echten BFF: Sitzungs-Cookies, unterdrückte Willkommens-/Push-Dialoge,
 * `isLoggedIn` und der angemeldete Benutzer. Alles Weitere mockt der Test selbst per `page.route`.
 */
export async function mockSession(page: Page, baseUrl: string): Promise<void> {
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
}
