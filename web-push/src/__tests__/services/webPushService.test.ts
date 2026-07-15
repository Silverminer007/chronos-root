import { describe, it, expect } from 'vitest'
import { WebPushService } from '../../services/WebPushService.js'
import { InMemoryPushSubscriptionRepository } from '../../repositories/InMemoryPushSubscriptionRepository.js'
import { MockWebPushAdapter } from '../../adapters/MockWebPushAdapter.js'

const USER_UUID = 'aaaaaaaa-0000-0000-0000-000000000001'
const EP1 = 'https://push.example.com/1'
const EP2 = 'https://push.example.com/2'

function makeService() {
  const repo = new InMemoryPushSubscriptionRepository()
  const push = new MockWebPushAdapter()
  repo.seedUser(USER_UUID, 1n)
  repo.seed([
    { endpoint: EP1, auth: 'a1', p256dh: 'p1', userId: 1n },
    { endpoint: EP2, auth: 'a2', p256dh: 'p2', userId: 1n },
  ])
  return { service: new WebPushService(repo, push), repo, push }
}

describe('WebPushService.sendRaw', () => {
  it('sends payload to all subscriptions for the user', async () => {
    const { service, push } = makeService()
    await service.sendRaw(USER_UUID, 'raw-payload')
    expect(push.sent.map((s) => s.payload)).toEqual(['raw-payload', 'raw-payload'])
    expect(push.sent.map((s) => s.endpoint)).toContain(EP1)
    expect(push.sent.map((s) => s.endpoint)).toContain(EP2)
  })

  it('deletes subscription on 410 Gone and continues with others', async () => {
    const { service, repo, push } = makeService()
    push.failFor(EP1, 'gone')
    await service.sendRaw(USER_UUID, 'payload')
    expect(await repo.isKnown(EP1)).toBe(false)
    expect(await repo.isKnown(EP2)).toBe(true)
    expect(push.sent).toHaveLength(1)
    expect(push.sent[0].endpoint).toBe(EP2)
  })

  it('keeps subscription on non-gone failure', async () => {
    const { service, repo, push } = makeService()
    push.failFor(EP1, 'error')
    await service.sendRaw(USER_UUID, 'payload')
    expect(await repo.isKnown(EP1)).toBe(true)
  })

  it('does nothing when user has no subscriptions', async () => {
    const { service, push } = makeService()
    await service.sendRaw('no-subs-user', 'payload')
    expect(push.sent).toHaveLength(0)
  })
})

describe('WebPushService.sendNotify', () => {
  it('sends structured JSON payload', async () => {
    const { service, push } = makeService()
    await service.sendNotify(USER_UUID, {
      title: 'Test',
      message: 'Body text',
      actions: [{ action: 'dismiss', title: 'Dismiss' }],
    })
    expect(push.sent).toHaveLength(2)
    const payload = JSON.parse(push.sent[0].payload)
    expect(payload).toEqual({
      title: 'Test',
      body: 'Body text',
      actions: [{ action: 'dismiss', title: 'Dismiss' }],
    })
  })

  it('sends empty actions array when none provided', async () => {
    const { service, push } = makeService()
    await service.sendNotify(USER_UUID, { title: 'T', message: 'M', actions: [] })
    const payload = JSON.parse(push.sent[0].payload)
    expect(payload.actions).toEqual([])
  })
})
