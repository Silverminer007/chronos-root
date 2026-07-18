export interface PushSubscription {
  id: bigint
  endpoint: string
  auth: string
  p256dh: string
  userId: bigint
}

export interface IPushSubscriptionRepository {
  findByEndpoint(endpoint: string): Promise<PushSubscription | undefined>
  findByKeycloakId(keycloakId: string): Promise<PushSubscription[]>
  save(keycloakId: string, endpoint: string, auth: string, p256dh: string): Promise<void>
  deleteByEndpoint(endpoint: string): Promise<void>
  isKnown(endpoint: string): Promise<boolean>
}
