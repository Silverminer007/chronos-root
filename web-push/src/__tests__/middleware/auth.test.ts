import { describe, it, expect, beforeEach } from 'vitest'
import { buildApp } from '../../app.js'
import { InMemoryPushSubscriptionRepository } from '../../repositories/InMemoryPushSubscriptionRepository.js'
import { MockWebPushAdapter } from '../../adapters/MockWebPushAdapter.js'
import { MockJwtVerifier } from '../../middleware/MockJwtVerifier.js'

function makeApp() {
  const repo = new InMemoryPushSubscriptionRepository()
  const push = new MockWebPushAdapter()
  const verifier = new MockJwtVerifier()
  verifier.register('valid-token', { sub: 'user-uuid-1' })
  verifier.register('send-token', { sub: 'user-uuid-2', scope: 'push:send' })
  return { app: buildApp({ repo, push, verifier }), repo, push, verifier }
}

describe('auth middleware', () => {
  it('rejects requests with no Authorization header', async () => {
    const { app } = makeApp()
    const res = await app.inject({ method: 'GET', url: '/api/v2/push/public-key' })
    expect(res.statusCode).toBe(401)
  })

  it('rejects requests with non-Bearer scheme', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'GET',
      url: '/api/v2/push/public-key',
      headers: { authorization: 'Basic dXNlcjpwYXNz' },
    })
    expect(res.statusCode).toBe(401)
  })

  it('rejects unknown/invalid tokens', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'GET',
      url: '/api/v2/push/public-key',
      headers: { authorization: 'Bearer bad-token' },
    })
    expect(res.statusCode).toBe(401)
  })

  it('passes valid tokens through', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'GET',
      url: '/api/v2/push/public-key',
      headers: { authorization: 'Bearer valid-token' },
    })
    expect(res.statusCode).toBe(200)
  })
})

describe('scope enforcement for push:send', () => {
  it('rejects authenticated users without push:send scope on /send', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: { authorization: 'Bearer valid-token', 'content-type': 'application/json' },
      payload: JSON.stringify({ userId: '00000000-0000-0000-0000-000000000001', payload: 'hello' }),
    })
    expect(res.statusCode).toBe(403)
  })

  it('rejects authenticated users without push:send scope on /notify', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/notify',
      headers: { authorization: 'Bearer valid-token', 'content-type': 'application/json' },
      payload: JSON.stringify({
        userId: '00000000-0000-0000-0000-000000000001',
        title: 'Hi',
        message: 'World',
        actions: [],
      }),
    })
    expect(res.statusCode).toBe(403)
  })

  it('allows tokens with push:send scope on /send', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: { authorization: 'Bearer send-token', 'content-type': 'application/json' },
      payload: JSON.stringify({ userId: '00000000-0000-0000-0000-000000000001', payload: 'hello' }),
    })
    expect(res.statusCode).toBe(204)
  })
})
