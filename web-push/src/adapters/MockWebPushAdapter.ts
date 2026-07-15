import type { IWebPushAdapter, SendResult } from './IWebPushAdapter.js'

export class MockWebPushAdapter implements IWebPushAdapter {
  private readonly publicKey: string
  readonly sent: Array<{ endpoint: string; auth: string; p256dh: string; payload: string }> = []
  private failEndpoints: Map<string, 'gone' | 'error'> = new Map()

  constructor(publicKey = 'mock-vapid-public-key') {
    this.publicKey = publicKey
  }

  failFor(endpoint: string, reason: 'gone' | 'error'): void {
    this.failEndpoints.set(endpoint, reason)
  }

  getPublicKey(): string {
    return this.publicKey
  }

  async send(endpoint: string, auth: string, p256dh: string, payload: string): Promise<SendResult> {
    const failure = this.failEndpoints.get(endpoint)
    if (failure === 'gone') return { ok: false, gone: true, error: new Error('410 Gone') }
    if (failure === 'error') return { ok: false, gone: false, error: new Error('500 Internal') }
    this.sent.push({ endpoint, auth, p256dh, payload })
    return { ok: true }
  }
}
