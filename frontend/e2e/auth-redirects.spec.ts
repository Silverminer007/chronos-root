import { test, expect } from '@playwright/test'

// The global route middleware (app/middleware/auth.global.ts) redirects to '/'
// for any unauthenticated request that is not:
//   - the root path '/'
//   - under '/public/*'
//   - under '/invite/*'  (invite links are intentionally public)
// These tests verify that redirect behaviour is intact.

test.describe('Auth redirects', () => {
  const protectedRoutes = [
    '/agenda', '/settings', '/profile', '/friends', '/groups',
    '/teams', '/teams/1', '/teams/1/invites',
  ]

  for (const route of protectedRoutes) {
    test(`${route} redirects unauthenticated visitor to landing page`, async ({ page }) => {
      await page.goto(route, { waitUntil: 'domcontentloaded' })
      await expect(page).toHaveURL('/')
      // Confirm the landing page is shown, not an error page
      await expect(page.getByRole('heading', { name: /Terminplanung/ })).toBeVisible()
    })
  }
})
