import type { IPushSubscriptionRepository, PushSubscription } from './IPushSubscriptionRepository.js'

export class InMemoryPushSubscriptionRepository implements IPushSubscriptionRepository {
  private subscriptions: PushSubscription[] = []
  private nextId = 1n
  // Maps keycloakId → internal user bigint id (simplified for tests)
  private userMap: Map<string, bigint> = new Map()
  private nextUserId = 1n

  seed(subs: Omit<PushSubscription, 'id'>[]): void {
    this.subscriptions = subs.map((s) => ({ ...s, id: this.nextId++ }))
  }

  seedUser(keycloakId: string, userId: bigint): void {
    this.userMap.set(keycloakId, userId)
  }

  async findByEndpoint(endpoint: string): Promise<PushSubscription | undefined> {
    return this.subscriptions.find((s) => s.endpoint === endpoint)
  }

  async findByKeycloakId(keycloakId: string): Promise<PushSubscription[]> {
    const userId = this.userMap.get(keycloakId)
    if (userId === undefined) return []
    return this.subscriptions.filter((s) => s.userId === userId)
  }

  async save(keycloakId: string, endpoint: string, auth: string, p256dh: string): Promise<void> {
    let userId = this.userMap.get(keycloakId)
    if (userId === undefined) {
      userId = this.nextUserId++
      this.userMap.set(keycloakId, userId)
    }
    // Replace existing subscription with same endpoint
    this.subscriptions = this.subscriptions.filter((s) => s.endpoint !== endpoint)
    this.subscriptions.push({ id: this.nextId++, endpoint, auth, p256dh, userId })
  }

  async deleteByEndpoint(endpoint: string): Promise<void> {
    this.subscriptions = this.subscriptions.filter((s) => s.endpoint !== endpoint)
  }

  async isKnown(endpoint: string): Promise<boolean> {
    return this.subscriptions.some((s) => s.endpoint === endpoint)
  }
}
