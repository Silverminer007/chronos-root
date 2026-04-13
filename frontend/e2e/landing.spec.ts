import { test, expect } from '@playwright/test'

// The landing page does `await fetchUser()` during SSR which proxies to the
// Quarkus backend. In CI the backend is absent, so the fetch fails with
// ECONNREFUSED (fast) and the auth store falls back to unauthenticated state.
// Tests use 'domcontentloaded' to avoid waiting for background network activity.

test.describe('Landing Page', () => {
  test('renders hero heading and tagline', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible()
    await expect(page.getByText('Für Jugendverbände gemacht')).toBeVisible()
  })

  test('shows Anmelden and Registrieren buttons', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('link', { name: 'Anmelden' }).first()).toBeVisible()
    await expect(page.getByRole('link', { name: 'Registrieren' }).first()).toBeVisible()
  })

  test('Anmelden links to /api/auth/login', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('link', { name: 'Anmelden' }).first())
      .toHaveAttribute('href', '/api/auth/login')
  })

  test('Registrieren links to /api/auth/register', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('link', { name: 'Registrieren' }).first())
      .toHaveAttribute('href', '/api/auth/register')
  })

  test('features section is present', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Alles, was dein Team braucht' })).toBeVisible()
  })

  test('all four feature cards are present', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Termine organisieren' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Verfügbarkeit abfragen' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Push-Benachrichtigungen' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Einfache Zusagen' })).toBeVisible()
  })

  test('CTA section is present', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Bereit loszulegen?' })).toBeVisible()
  })
})
