import { test, expect } from '@playwright/test'

// Teams pages require authentication and redirect unauthenticated users to /.
// The invite redemption page (/invite/[token]) is intentionally public so that
// users can see the join prompt before logging in.

test.describe('Invite page (public)', () => {
  test('renders without authentication', async ({ page }) => {
    await page.goto('/invite/some-token', { waitUntil: 'domcontentloaded' })
    // Must NOT be redirected away
    await expect(page).toHaveURL('/invite/some-token')
  })

  test('shows Team-Einladung heading', async ({ page }) => {
    await page.goto('/invite/some-token', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Team-Einladung' })).toBeVisible()
  })

  test('shows Anmelden button for unauthenticated visitor', async ({ page }) => {
    await page.goto('/invite/some-token', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('link', { name: 'Anmelden' })).toBeVisible()
  })

  test('Anmelden link points to auth login endpoint', async ({ page }) => {
    await page.goto('/invite/some-token', { waitUntil: 'domcontentloaded' })
    const loginLink = page.getByRole('link', { name: 'Anmelden' })
    await expect(loginLink).toHaveAttribute('href', /\/api\/auth\/login/)
  })

  test('Anmelden link encodes the return path', async ({ page }) => {
    await page.goto('/invite/abc-123', { waitUntil: 'domcontentloaded' })
    const loginLink = page.getByRole('link', { name: 'Anmelden' })
    const href = await loginLink.getAttribute('href')
    expect(href).toContain('returnTo=')
    expect(href).toContain('invite')
  })

  test('shows link back to landing page', async ({ page }) => {
    await page.goto('/invite/some-token', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('link', { name: 'Zur Startseite' })).toBeVisible()
  })
})

test.describe('Teams routes (auth required)', () => {
  test('/teams redirects unauthenticated visitor to /', async ({ page }) => {
    await page.goto('/teams', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible()
  })

  test('/teams/1 redirects unauthenticated visitor to /', async ({ page }) => {
    await page.goto('/teams/1', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL('/')
  })

  test('/teams/1/invites redirects unauthenticated visitor to /', async ({ page }) => {
    await page.goto('/teams/1/invites', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL('/')
  })
})

test.describe('/friends redirect', () => {
  test('/friends redirects unauthenticated visitor to / (middleware runs before Vue redirect)', async ({ page }) => {
    await page.goto('/friends', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible()
  })
})
