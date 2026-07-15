import type { IPushSubscriptionRepository } from '../repositories/IPushSubscriptionRepository.js'
import type { IWebPushAdapter } from '../adapters/IWebPushAdapter.js'

export class PushSubscriptionService {
  constructor(
    private readonly repo: IPushSubscriptionRepository,
    private readonly push: IWebPushAdapter,
  ) {}

  getPublicKey(): string {
    return this.push.getPublicKey()
  }

  async subscribe(keycloakId: string, endpoint: string, auth: string, p256dh: string): Promise<void> {
    await this.repo.save(keycloakId, endpoint, auth, p256dh)
  }

  async unsubscribe(endpoint: string): Promise<void> {
    await this.repo.deleteByEndpoint(endpoint)
  }

  async isKnown(endpoint: string): Promise<boolean> {
    return this.repo.isKnown(endpoint)
  }
}
