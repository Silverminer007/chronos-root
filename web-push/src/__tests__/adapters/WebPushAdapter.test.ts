import { describe, it, expect, vi, beforeEach } from 'vitest'
import { WebPushAdapter } from '../../adapters/WebPushAdapter.js'

// Mock the web-push module before any imports use it
vi.mock('web-push', () => ({
  default: {
    setVapidDetails: vi.fn(),
    sendNotification: vi.fn(),
  },
}))

const VAPID_CONFIG = {
  publicKey: 'BPublicKey==',
  privateKey: 'BPrivateKey==',
  mailto: 'admin@example.com',
}

const SUB = {
  endpoint: 'https://push.example.com/sub/abc',
  auth: 'auth-value',
  p256dh: 'p256dh-value',
}

async function getWebPushMock() {
  const mod = await import('web-push')
  return mod.default as {
    setVapidDetails: ReturnType<typeof vi.fn>
    sendNotification: ReturnType<typeof vi.fn>
  }
}

describe('WebPushAdapter', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
  })

  it('calls setVapidDetails with the correct arguments on construction', async () => {
    const mock = await getWebPushMock()
    new WebPushAdapter(VAPID_CONFIG)
    expect(mock.setVapidDetails).toHaveBeenCalledOnce()
    expect(mock.setVapidDetails).toHaveBeenCalledWith(
      'mailto:admin@example.com',
      VAPID_CONFIG.publicKey,
      VAPID_CONFIG.privateKey,
    )
  })

  it('getPublicKey() returns the configured public key', () => {
    const adapter = new WebPushAdapter(VAPID_CONFIG)
    expect(adapter.getPublicKey()).toBe(VAPID_CONFIG.publicKey)
  })

  describe('send()', () => {
    it('returns { ok: true } on successful delivery', async () => {
      const mock = await getWebPushMock()
      mock.sendNotification.mockResolvedValue({ statusCode: 201 })
      const adapter = new WebPushAdapter(VAPID_CONFIG)

      const result = await adapter.send(SUB.endpoint, SUB.auth, SUB.p256dh, 'payload')

      expect(result).toEqual({ ok: true })
      expect(mock.sendNotification).toHaveBeenCalledOnce()
      expect(mock.sendNotification).toHaveBeenCalledWith(
        { endpoint: SUB.endpoint, keys: { auth: SUB.auth, p256dh: SUB.p256dh } },
        'payload',
      )
    })

    it('returns { ok: false, gone: true } on 410 Gone', async () => {
      const mock = await getWebPushMock()
      const err = Object.assign(new Error('Gone'), { statusCode: 410 })
      mock.sendNotification.mockRejectedValue(err)
      const adapter = new WebPushAdapter(VAPID_CONFIG)

      const result = await adapter.send(SUB.endpoint, SUB.auth, SUB.p256dh, 'payload')

      expect(result.ok).toBe(false)
      if (!result.ok) {
        expect(result.gone).toBe(true)
        expect(result.error.message).toBe('Gone')
      }
    })

    it('returns { ok: false, gone: true } on 404 Not Found', async () => {
      const mock = await getWebPushMock()
      const err = Object.assign(new Error('Not Found'), { statusCode: 404 })
      mock.sendNotification.mockRejectedValue(err)
      const adapter = new WebPushAdapter(VAPID_CONFIG)

      const result = await adapter.send(SUB.endpoint, SUB.auth, SUB.p256dh, 'payload')

      expect(result.ok).toBe(false)
      if (!result.ok) expect(result.gone).toBe(true)
    })

    it('returns { ok: false, gone: false } on 500 server error', async () => {
      const mock = await getWebPushMock()
      const err = Object.assign(new Error('Internal Server Error'), { statusCode: 500 })
      mock.sendNotification.mockRejectedValue(err)
      const adapter = new WebPushAdapter(VAPID_CONFIG)

      const result = await adapter.send(SUB.endpoint, SUB.auth, SUB.p256dh, 'payload')

      expect(result.ok).toBe(false)
      if (!result.ok) expect(result.gone).toBe(false)
    })

    it('returns { ok: false, gone: false } on network error (no statusCode)', async () => {
      const mock = await getWebPushMock()
      mock.sendNotification.mockRejectedValue(new Error('ECONNREFUSED'))
      const adapter = new WebPushAdapter(VAPID_CONFIG)

      const result = await adapter.send(SUB.endpoint, SUB.auth, SUB.p256dh, 'payload')

      expect(result.ok).toBe(false)
      if (!result.ok) {
        expect(result.gone).toBe(false)
        expect(result.error.message).toBe('ECONNREFUSED')
      }
    })

    it('wraps non-Error throws in an Error object', async () => {
      const mock = await getWebPushMock()
      mock.sendNotification.mockRejectedValue('string error')
      const adapter = new WebPushAdapter(VAPID_CONFIG)

      const result = await adapter.send(SUB.endpoint, SUB.auth, SUB.p256dh, 'payload')

      expect(result.ok).toBe(false)
      if (!result.ok) expect(result.error).toBeInstanceOf(Error)
    })
  })
})
