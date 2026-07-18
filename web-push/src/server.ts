import { loadConfig } from './config/env.js'
import { buildApp } from './app.js'

// Real implementations — only imported here so tests can use mocks
async function main() {
  const config = loadConfig()

  // Lazy imports keep real deps out of the test bundle
  const { KyselyPushSubscriptionRepository } = await import(
    './repositories/KyselyPushSubscriptionRepository.js'
  )
  const { WebPushAdapter } = await import('./adapters/WebPushAdapter.js')
  const { KeycloakJwtVerifier } = await import('./middleware/KeycloakJwtVerifier.js')

  const repo = new KyselyPushSubscriptionRepository({
    host: config.DB_HOST,
    port: config.DB_PORT,
    database: config.DB_NAME,
    user: config.DB_USER,
    password: config.DB_PASSWORD,
  })

  const push = new WebPushAdapter({
    publicKey: config.VAPID_PUBLIC_KEY,
    privateKey: config.VAPID_PRIVATE_KEY,
    mailto: config.VAPID_MAILTO,
  })

  const verifier = new KeycloakJwtVerifier(config.KEYCLOAK_ISSUER)

  const app = buildApp({ repo, push, verifier })

  await app.listen({ port: config.PORT, host: '0.0.0.0' })
  console.log(`web-push service listening on port ${config.PORT}`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
