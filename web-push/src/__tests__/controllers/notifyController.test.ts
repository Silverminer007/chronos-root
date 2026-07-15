import { describe, it, expect, beforeEach } from 'vitest'
import { buildApp } from '../../app.js'
import { InMemoryPushSubscriptionRepository } from '../../repositories/InMemoryPushSubscriptionRepository.js'
import { MockWebPushAdapter } from '../../adapters/MockWebPushAdapter.js'
import { MockJwtVerifier } from '../../middleware/MockJwtVerifier.js'

const SENDER_UUID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
const TARGET_UUID = 'ffffffff-eeee-dddd-cccc-bbbbbbbbbbbb'
const ENDPOINT_1 = 'https://push.example.com/sub/1'
const ENDPOINT_2 = 'https://push.example.com/sub/2'

function makeApp() {
  const repo = new InMemoryPushSubscriptionRepository()
  const push = new MockWebPushAdapter()
  const verifier = new MockJwtVerifier()
  verifier.register('send-token', { sub: SENDER_UUID, scope: 'push:send' })

  // Pre-register target user's subscriptions
  repo.seedUser(TARGET_UUID, 99n)
  repo.seed([
    { endpoint: ENDPOINT_1, auth: 'auth1', p256dh: 'p256dh1', userId: 99n },
    { endpoint: ENDPOINT_2, auth: 'auth2', p256dh: 'p256dh2', userId: 99n },
  ])

  return { app: buildApp({ repo, push, verifier }), repo, push }
}

const sendHeaders = {
  authorization: 'Bearer send-token',
  'content-type': 'application/json',
}

describe('POST /api/v2/push/send', () => {
  it('sends raw payload to all subscriptions for the target user', async () => {
    const { app, push } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID, payload: '{"type":"test"}' }),
    })
    expect(res.statusCode).toBe(204)
    expect(push.sent).toHaveLength(2)
    expect(push.sent.map((s) => s.endpoint)).toContain(ENDPOINT_1)
    expect(push.sent.map((s) => s.endpoint)).toContain(ENDPOINT_2)
    expect(push.sent[0].payload).toBe('{"type":"test"}')
  })

  it('sends nothing when user has no subscriptions', async () => {
    const { app, push } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: '00000000-0000-0000-0000-000000000000', payload: 'hi' }),
    })
    expect(res.statusCode).toBe(204)
    expect(push.sent).toHaveLength(0)
  })

  it('deletes a gone subscription and still sends to remaining ones', async () => {
    const { app, repo, push } = makeApp()
    push.failFor(ENDPOINT_1, 'gone')

    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID, payload: 'hello' }),
    })

    expect(res.statusCode).toBe(204)
    expect(push.sent).toHaveLength(1)
    expect(push.sent[0].endpoint).toBe(ENDPOINT_2)
    expect(await repo.isKnown(ENDPOINT_1)).toBe(false)
    expect(await repo.isKnown(ENDPOINT_2)).toBe(true)
  })

  it('does not delete a subscription on non-gone push failure', async () => {
    const { app, repo, push } = makeApp()
    push.failFor(ENDPOINT_1, 'error')

    await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID, payload: 'hello' }),
    })

    expect(await repo.isKnown(ENDPOINT_1)).toBe(true)
  })

  it('returns 400 when userId is not a UUID', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: 'not-a-uuid', payload: 'hi' }),
    })
    expect(res.statusCode).toBe(400)
  })

  it('returns 400 when payload is missing', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/send',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID }),
    })
    expect(res.statusCode).toBe(400)
  })
})

describe('POST /api/v2/push/notify', () => {
  it('sends structured payload to all subscriptions', async () => {
    const { app, push } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/notify',
      headers: sendHeaders,
      payload: JSON.stringify({
        userId: TARGET_UUID,
        title: 'Hello',
        message: 'World',
        actions: [{ action: 'open', title: 'Open App' }],
      }),
    })
    expect(res.statusCode).toBe(204)
    expect(push.sent).toHaveLength(2)
    const sent = JSON.parse(push.sent[0].payload)
    expect(sent.title).toBe('Hello')
    expect(sent.body).toBe('World')
    expect(sent.actions).toEqual([{ action: 'open', title: 'Open App' }])
  })

  it('accepts empty actions array', async () => {
    const { app, push } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/notify',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID, title: 'Hi', message: 'There', actions: [] }),
    })
    expect(res.statusCode).toBe(204)
    const sent = JSON.parse(push.sent[0].payload)
    expect(sent.actions).toEqual([])
  })

  it('uses empty actions array when actions field is omitted', async () => {
    const { app, push } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/notify',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID, title: 'Hi', message: 'There' }),
    })
    expect(res.statusCode).toBe(204)
    const sent = JSON.parse(push.sent[0].payload)
    expect(sent.actions).toEqual([])
  })

  it('deletes gone subscriptions on notify too', async () => {
    const { app, repo, push } = makeApp()
    push.failFor(ENDPOINT_2, 'gone')

    await app.inject({
      method: 'POST',
      url: '/api/v2/push/notify',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID, title: 'Hi', message: 'There' }),
    })

    expect(await repo.isKnown(ENDPOINT_2)).toBe(false)
  })

  it('returns 400 when title is missing', async () => {
    const { app } = makeApp()
    const res = await app.inject({
      method: 'POST',
      url: '/api/v2/push/notify',
      headers: sendHeaders,
      payload: JSON.stringify({ userId: TARGET_UUID, message: 'no title' }),
    })
    expect(res.statusCode).toBe(400)
  })
})
