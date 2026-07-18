import Fastify from 'fastify'
import type { IPushSubscriptionRepository } from './repositories/IPushSubscriptionRepository.js'
import type { IWebPushAdapter } from './adapters/IWebPushAdapter.js'
import type { IJwtVerifier } from './middleware/IJwtVerifier.js'
import { PushSubscriptionService } from './services/PushSubscriptionService.js'
import { WebPushService } from './services/WebPushService.js'
import { pushRoutes } from './routes/push.js'

export interface AppDependencies {
  repo: IPushSubscriptionRepository
  push: IWebPushAdapter
  verifier: IJwtVerifier
}

export function buildApp(deps: AppDependencies) {
  const app = Fastify({ logger: false })

  app.get('/health', async (_req, reply) => reply.send({ status: 'ok' }))

  const subscriptionService = new PushSubscriptionService(deps.repo, deps.push)
  const webPushService = new WebPushService(deps.repo, deps.push)

  app.register(pushRoutes, {
    prefix: '/api/v2/push',
    subscriptionService,
    webPushService,
    verifier: deps.verifier,
  })

  return app
}
