import { describe, it, expect, beforeEach } from 'vitest'
import { buildApp } from '../../app.js'
import { InMemoryPushSubscriptionRepository } from '../../repositories/InMemoryPushSubscriptionRepository.js'
import { MockWebPushAdapter } from '../../adapters/MockWebPushAdapter.js'
import { MockJwtVerifier } from '../../middleware/MockJwtVerifier.js'

const USER_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
const ENDPOINT = 'https://push.example.com/sub/abc'

function makeApp() {
  const repo = new InMemoryPushSubscriptionRepository()
  const push = new MockWebPushAdapter('test-public-key')
  const verifier = new MockJwtVerifier()
  verifier.register('token', { sub: USER_ID })
  const app = buildApp({ repo, push, verifier })
  return { app, repo, push }
}

const authHeader = { authorization: 'Bearer token' }
const jsonHeaders = { ...authHeader, 'content-type': 'application/json' }

describe('GET /api/v2/push/public-key', () => {
  it('returns the VAPID public key as plain text', async () => {
    const { app } = makeApp()
    const res = await app.inject({ method: 'GET', url: '/api/v2/push/public-key', headers: authHeader })
    expect(res.statusCode).toBe(200)
    expect(res.body).toBe('test-public-key')
  })
})

describe('POST /api/v2/push/subscribe', () => {
  it('saves a subscription and returns 204', async () => {
    const { app, repo } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/subscribe',
      headers: jsonHeaders,
      payload: JSON.stringify({ endpoint: ENDPOINT, keys: { p256dh: 'key1', auth: 'auth1' } }),
    })
    expect(res.statusCode).toBe(204)
    expect(await repo.isKnown(ENDPOINT)).toBe(true)
  })

  it('replaces an existing subscription with the same endpoint', async () => {
    const { app, repo } = makeApp()
    await app.inject({
      method: 'POST',
      url: '/api/v2/push/subscribe',
      headers: jsonHeaders,
      payload: JSON.stringify({ endpoint: ENDPOINT, keys: { p256dh: 'old-key', auth: 'old-auth' } }),
    })
    await app.inject({
      method: 'POST',
      url: '/api/v2/push/subscribe',
      headers: jsonHeaders,
      payload: JSON.stringify({ endpoint: ENDPOINT, keys: { p256dh: 'new-key', auth: 'new-auth' } }),
    })
    const subs = await repo.findByKeycloakId(USER_ID)
    expect(subs).toHaveLength(1)
    expect(subs[0].p256dh).toBe('new-key')
  })

  it('rejects invalid body with 400', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/subscribe',
      headers: jsonHeaders,
      payload: JSON.stringify({ endpoint: 'not-a-url' }),
    })
    expect(res.statusCode).toBe(400)
  })
})

describe('DELETE /api/v2/push/unsubscribe', () => {
  it('removes the subscription and returns 204', async () => {
    const { app, repo } = makeApp()
    repo.seed([{ endpoint: ENDPOINT, auth: 'auth1', p256dh: 'key1', userId: 1n }])
    const res = await app.inject({
      method: 'DELETE',
      url: `/api/v2/push/unsubscribe?endpoint=${encodeURIComponent(ENDPOINT)}`,
      headers: authHeader,
    })
    expect(res.statusCode).toBe(204)
    expect(await repo.isKnown(ENDPOINT)).toBe(false)
  })

  it('returns 400 when endpoint query param is missing', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'DELETE',
      url: '/api/v2/push/unsubscribe',
      headers: authHeader,
    })
    expect(res.statusCode).toBe(400)
  })

  it('is idempotent — returns 204 even when subscription does not exist', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'DELETE',
      url: `/api/v2/push/unsubscribe?endpoint=${encodeURIComponent(ENDPOINT)}`,
      headers: authHeader,
    })
    expect(res.statusCode).toBe(204)
  })
})

describe('GET /api/v2/push/status', () => {
  it('returns { exists: true } when endpoint is registered', async () => {
    const { app, repo } = makeApp()
    repo.seed([{ endpoint: ENDPOINT, auth: 'a', p256dh: 'p', userId: 1n }])
    const res = await app.inject({
      method: 'GET',
      url: `/api/v2/push/status?endpoint=${encodeURIComponent(ENDPOINT)}`,
      headers: authHeader,
    })
    expect(res.statusCode).toBe(200)
    expect(JSON.parse(res.body)).toEqual({ exists: true })
  })

  it('returns { exists: false } when endpoint is not registered', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'GET',
      url: `/api/v2/push/status?endpoint=${encodeURIComponent(ENDPOINT)}`,
      headers: authHeader,
    })
    expect(res.statusCode).toBe(200)
    expect(JSON.parse(res.body)).toEqual({ exists: false })
  })

  it('returns 400 when endpoint query param is missing', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'GET',
      url: '/api/v2/push/status',
      headers: authHeader,
    })
    expect(res.statusCode).toBe(400)
  })
})
