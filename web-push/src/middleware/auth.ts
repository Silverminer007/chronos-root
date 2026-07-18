import type { FastifyRequest, FastifyReply } from 'fastify'
import type { IJwtVerifier, VerifiedToken } from './IJwtVerifier.js'

declare module 'fastify' {
  interface FastifyRequest {
    user: VerifiedToken
  }
}

export function buildAuthHook(verifier: IJwtVerifier) {
  return async function authenticate(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const header = request.headers.authorization
    if (!header?.startsWith('Bearer ')) {
      reply.code(401).send({ error: 'Missing Bearer token' })
      return
    }
    const token = header.slice(7)
    try {
      request.user = await verifier.verify(token)
    } catch {
      reply.code(401).send({ error: 'Invalid or expired token' })
    }
  }
}

export function buildScopeHook(requiredScope: string) {
  return async function requireScope(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const scopes = (request.user.scope ?? '').split(' ')
    if (!scopes.includes(requiredScope)) {
      reply.code(403).send({ error: `Missing required scope: ${requiredScope}` })
    }
  }
}
