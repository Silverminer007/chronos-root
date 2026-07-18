import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { buildAuthHook, buildScopeHook } from '../middleware/auth.js'
import type { PushSubscriptionService } from '../services/PushSubscriptionService.js'
import type { WebPushService } from '../services/WebPushService.js'
import type { IJwtVerifier } from '../middleware/IJwtVerifier.js'

const subscribeBody = z.object({
  endpoint: z.string().url(),
  keys: z.object({
    p256dh: z.string().min(1),
    auth: z.string().min(1),
  }),
})

const sendRawBody = z.object({
  userId: z.string().uuid(),
  payload: z.string().min(1),
})

const notifyBody = z.object({
  userId: z.string().uuid(),
  title: z.string().min(1),
  message: z.string().min(1),
  actions: z
    .array(
      z.object({
        action: z.string(),
        title: z.string(),
        icon: z.string().optional(),
      }),
    )
    .default([]),
})

export async function pushRoutes(
  app: FastifyInstance,
  opts: {
    subscriptionService: PushSubscriptionService
    webPushService: WebPushService
    verifier: IJwtVerifier
  },
): Promise<void> {
  const { subscriptionService, webPushService, verifier } = opts
  const authenticate = buildAuthHook(verifier)
  const requireSendScope = buildScopeHook('push:send')

  app.get('/public-key', { preHandler: [authenticate] }, async (_req, reply) => {
    return reply.send(subscriptionService.getPublicKey())
  })

  app.post('/subscribe', { preHandler: [authenticate] }, async (req, reply) => {
    const body = subscribeBody.safeParse(req.body)
    if (!body.success) return reply.code(400).send({ error: body.error.flatten() })
    await subscriptionService.subscribe(
      req.user.sub,
      body.data.endpoint,
      body.data.keys.auth,
      body.data.keys.p256dh,
    )
    return reply.code(204).send()
  })

  app.delete('/unsubscribe', { preHandler: [authenticate] }, async (req, reply) => {
    const endpoint = (req.query as Record<string, string>).endpoint
    if (!endpoint) return reply.code(400).send({ error: 'Missing ?endpoint query parameter' })
    await subscriptionService.unsubscribe(endpoint)
    return reply.code(204).send()
  })

  app.get('/status', { preHandler: [authenticate] }, async (req, reply) => {
    const endpoint = (req.query as Record<string, string>).endpoint
    if (!endpoint) return reply.code(400).send({ error: 'Missing ?endpoint query parameter' })
    const exists = await subscriptionService.isKnown(endpoint)
    return reply.send({ exists })
  })

  app.post('/send', { preHandler: [authenticate, requireSendScope] }, async (req, reply) => {
    const body = sendRawBody.safeParse(req.body)
    if (!body.success) return reply.code(400).send({ error: body.error.flatten() })
    await webPushService.sendRaw(body.data.userId, body.data.payload)
    return reply.code(204).send()
  })

  app.post('/notify', { preHandler: [authenticate, requireSendScope] }, async (req, reply) => {
    const body = notifyBody.safeParse(req.body)
    if (!body.success) return reply.code(400).send({ error: body.error.flatten() })
    await webPushService.sendNotify(body.data.userId, {
      title: body.data.title,
      message: body.data.message,
      actions: body.data.actions,
    })
    return reply.code(204).send()
  })
}
