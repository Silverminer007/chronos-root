import { expect, test } from '@playwright/test';

// Login-Redirect (der Link auf der Landingpage ist in landing.spec.ts abgedeckt): Der BFF leitet auf den Keycloak-Authorization-Endpoint (Issuer aus der Playwright-Konfiguration).
test.describe('Login', () => {
  test('/api/auth/login leitet zum Authorization-Endpoint von Keycloak', async ({ request }) => {
    const response = await request.get('/api/auth/login', { maxRedirects: 0 });

    expect(response.status()).toBe(302);
    const location = new URL(response.headers()['location']);
    expect(location.origin + location.pathname).toBe('http://localhost:1/realms/e2e/protocol/openid-connect/auth');
    expect(location.searchParams.get('client_id')).toBe('e2e');
    expect(location.searchParams.get('response_type')).toBe('code');
    expect(location.searchParams.get('redirect_uri')).toMatch(/\/api\/auth\/callback$/);
  });
});
