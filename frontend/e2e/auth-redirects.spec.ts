import { test, expect } from '@playwright/test'

// The server middleware (server/middleware/refresh.global.ts) sends a 301
// redirect to '/' for any request without cookies that is not:
//   - the root path '/'
//   - under '/api/*'
//   - under '/public/*'
// These tests verify that redirect behaviour is intact.

test.describe('Auth redirects', () => {
  const protectedRoutes = ['/agenda', '/settings', '/profile', '/friends', '/groups']

  for (const route of protectedRoutes) {
    test(`${route} redirects unauthenticated visitor to landing page`, async ({ page }) => {
      await page.goto(route, { waitUntil: 'domcontentloaded' })
      await expect(page).toHaveURL('/')
      // Confirm the landing page is shown, not an error page
      await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible()
    })
  }
})
