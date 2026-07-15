import webPush from 'web-push'
import type { IWebPushAdapter, SendResult } from './IWebPushAdapter.js'

export interface VapidConfig {
  publicKey: string
  privateKey: string
  mailto: string
}

export class WebPushAdapter implements IWebPushAdapter {
  private readonly publicKey: string

  constructor(config: VapidConfig) {
    this.publicKey = config.publicKey
    webPush.setVapidDetails(`mailto:${config.mailto}`, config.publicKey, config.privateKey)
  }

  getPublicKey(): string {
    return this.publicKey
  }

  async send(endpoint: string, auth: string, p256dh: string, payload: string): Promise<SendResult> {
    try {
      await webPush.sendNotification({ endpoint, keys: { auth, p256dh } }, payload)
      return { ok: true }
    } catch (err) {
      const statusCode = (err as { statusCode?: number }).statusCode
      const gone = statusCode === 410 || statusCode === 404
      return { ok: false, gone, error: err instanceof Error ? err : new Error(String(err)) }
    }
  }
}
