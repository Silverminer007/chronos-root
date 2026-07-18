import type { IPushSubscriptionRepository } from '../repositories/IPushSubscriptionRepository.js'
import type { IWebPushAdapter } from '../adapters/IWebPushAdapter.js'

export interface NotifyOptions {
  title: string
  message: string
  actions: Array<{ action: string; title: string; icon?: string }>
}

export class WebPushService {
  constructor(
    private readonly repo: IPushSubscriptionRepository,
    private readonly push: IWebPushAdapter,
  ) {}

  async sendRaw(keycloakId: string, payload: string): Promise<void> {
    const subs = await this.repo.findByKeycloakId(keycloakId)
    await this.sendToAll(subs, payload)
  }

  async sendNotify(keycloakId: string, opts: NotifyOptions): Promise<void> {
    const payload = JSON.stringify({
      title: opts.title,
      body: opts.message,
      actions: opts.actions,
    })
    const subs = await this.repo.findByKeycloakId(keycloakId)
    await this.sendToAll(subs, payload)
  }

  private async sendToAll(
    subs: Array<{ endpoint: string; auth: string; p256dh: string }>,
    payload: string,
  ): Promise<void> {
    await Promise.all(
      subs.map(async (sub) => {
        const result = await this.push.send(sub.endpoint, sub.auth, sub.p256dh, payload)
        if (!result.ok && result.gone) {
          await this.repo.deleteByEndpoint(sub.endpoint)
        }
      }),
    )
  }
}
