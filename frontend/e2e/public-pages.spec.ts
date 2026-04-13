import { test, expect } from '@playwright/test'

test.describe('Public pages', () => {
  test('Impressum renders with heading', async ({ page }) => {
    await page.goto('/public/impressum', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Impressum' })).toBeVisible()
  })

  test('Impressum has Chronos logo link back to home', async ({ page }) => {
    await page.goto('/public/impressum', { waitUntil: 'domcontentloaded' })
    const logoLink = page.getByRole('link', { name: /Chronos/ }).first()
    await expect(logoLink).toBeVisible()
    await logoLink.click()
    await expect(page).toHaveURL('/')
  })

  test('Datenschutz page renders', async ({ page }) => {
    await page.goto('/public/datenschutz', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('h1').first()).toBeVisible()
  })

  test('Install page renders', async ({ page }) => {
    await page.goto('/public/install', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('body')).toBeVisible()
  })

  test('PWA manifest is served with correct content type', async ({ request }) => {
    const response = await request.get('/manifest.webmanifest')
    expect(response.status()).toBe(200)
    expect(response.headers()['content-type']).toContain('application/manifest+json')
  })

  test('PWA manifest has required fields', async ({ request }) => {
    const response = await request.get('/manifest.webmanifest')
    const manifest = await response.json()
    expect(manifest.name).toBeTruthy()
    expect(manifest.icons).toBeDefined()
    expect(Array.isArray(manifest.icons)).toBe(true)
    expect(manifest.start_url).toBeDefined()
  })
})
