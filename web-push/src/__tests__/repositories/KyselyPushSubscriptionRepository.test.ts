import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'
import { PostgreSqlContainer, type StartedPostgreSqlContainer } from '@testcontainers/postgresql'
import { Kysely, PostgresDialect, sql } from 'kysely'
import { Pool } from 'pg'
import { KyselyPushSubscriptionRepository } from '../../repositories/KyselyPushSubscriptionRepository.js'
import type { Database } from '../../repositories/db.js'

const ENDPOINT_1 = 'https://push.example.com/sub/1'
const ENDPOINT_2 = 'https://push.example.com/sub/2'
const KEYCLOAK_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'

let container: StartedPostgreSqlContainer
let db: Kysely<Database>
let repo: KyselyPushSubscriptionRepository

beforeAll(async () => {
  container = await new PostgreSqlContainer('postgres:16-alpine').start()

  db = new Kysely<Database>({
    dialect: new PostgresDialect({
      pool: new Pool({
        host: container.getHost(),
        port: container.getPort(),
        database: container.getDatabase(),
        user: container.getUsername(),
        password: container.getPassword(),
      }),
    }),
  })

  await sql`
    CREATE TABLE users (
      id        BIGSERIAL PRIMARY KEY,
      email     VARCHAR(255) UNIQUE,
      firstname VARCHAR(255),
      lastname  VARCHAR(255),
      oidcid    VARCHAR(255) UNIQUE
    )
  `.execute(db)

  await sql`
    CREATE TABLE pushsubscription (
      id       BIGSERIAL PRIMARY KEY,
      auth     VARCHAR(255) NOT NULL,
      endpoint VARCHAR(255) NOT NULL,
      p256dh   VARCHAR(255) NOT NULL,
      user_id  BIGINT       NOT NULL,
      CONSTRAINT fk_pushsubscription_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION
    )
  `.execute(db)

  repo = new KyselyPushSubscriptionRepository(db)
}, 60_000)

afterAll(async () => {
  await db.destroy()
  await container.stop()
})

beforeEach(async () => {
  await db.deleteFrom('pushsubscription').execute()
  await db.deleteFrom('users').execute()
})

async function insertUser(oidcid: string): Promise<bigint> {
  const result = await db
    .insertInto('users')
    .values({ oidcid, email: null, firstname: null, lastname: null })
    .returning('id')
    .executeTakeFirstOrThrow()
  return result.id
}

async function insertSub(userId: bigint, endpoint: string): Promise<void> {
  await db
    .insertInto('pushsubscription')
    .values({ user_id: userId, endpoint, auth: 'auth-value', p256dh: 'p256dh-value' })
    .execute()
}

describe('findByEndpoint', () => {
  it('returns the subscription when endpoint exists', async () => {
    const userId = await insertUser(KEYCLOAK_ID)
    await insertSub(userId, ENDPOINT_1)

    const sub = await repo.findByEndpoint(ENDPOINT_1)
    expect(sub).toBeDefined()
    expect(sub!.endpoint).toBe(ENDPOINT_1)
    expect(sub!.auth).toBe('auth-value')
    expect(sub!.p256dh).toBe('p256dh-value')
  })

  it('returns undefined when endpoint does not exist', async () => {
    const sub = await repo.findByEndpoint('https://does-not-exist.example.com')
    expect(sub).toBeUndefined()
  })
})

describe('findByKeycloakId', () => {
  it('returns all subscriptions for the given keycloak user', async () => {
    const userId = await insertUser(KEYCLOAK_ID)
    await insertSub(userId, ENDPOINT_1)
    await insertSub(userId, ENDPOINT_2)

    const subs = await repo.findByKeycloakId(KEYCLOAK_ID)
    expect(subs).toHaveLength(2)
    expect(subs.map((s) => s.endpoint)).toContain(ENDPOINT_1)
    expect(subs.map((s) => s.endpoint)).toContain(ENDPOINT_2)
  })

  it('returns empty array for unknown keycloak id', async () => {
    const subs = await repo.findByKeycloakId('unknown-id')
    expect(subs).toHaveLength(0)
  })

  it('does not return subscriptions belonging to other users', async () => {
    const userId1 = await insertUser(KEYCLOAK_ID)
    const userId2 = await insertUser('other-user-uuid')
    await insertSub(userId1, ENDPOINT_1)
    await insertSub(userId2, ENDPOINT_2)

    const subs = await repo.findByKeycloakId(KEYCLOAK_ID)
    expect(subs).toHaveLength(1)
    expect(subs[0].endpoint).toBe(ENDPOINT_1)
  })
})

describe('save', () => {
  it('persists a new subscription for a known user', async () => {
    await insertUser(KEYCLOAK_ID)

    await repo.save(KEYCLOAK_ID, ENDPOINT_1, 'auth-new', 'p256dh-new')

    const sub = await repo.findByEndpoint(ENDPOINT_1)
    expect(sub).toBeDefined()
    expect(sub!.auth).toBe('auth-new')
    expect(sub!.p256dh).toBe('p256dh-new')
  })

  it('replaces an existing subscription with the same endpoint', async () => {
    const userId = await insertUser(KEYCLOAK_ID)
    await insertSub(userId, ENDPOINT_1)

    await repo.save(KEYCLOAK_ID, ENDPOINT_1, 'new-auth', 'new-p256dh')

    const subs = await repo.findByKeycloakId(KEYCLOAK_ID)
    expect(subs).toHaveLength(1)
    expect(subs[0].auth).toBe('new-auth')
  })

  it('does nothing when the user does not exist in the users table', async () => {
    await repo.save('non-existent-oidcid', ENDPOINT_1, 'a', 'p')
    const sub = await repo.findByEndpoint(ENDPOINT_1)
    expect(sub).toBeUndefined()
  })
})

describe('deleteByEndpoint', () => {
  it('removes the subscription', async () => {
    const userId = await insertUser(KEYCLOAK_ID)
    await insertSub(userId, ENDPOINT_1)

    await repo.deleteByEndpoint(ENDPOINT_1)

    expect(await repo.isKnown(ENDPOINT_1)).toBe(false)
  })

  it('is idempotent — does not throw when endpoint is not found', async () => {
    await expect(repo.deleteByEndpoint('https://not-here.example.com')).resolves.not.toThrow()
  })
})

describe('isKnown', () => {
  it('returns true when the endpoint exists', async () => {
    const userId = await insertUser(KEYCLOAK_ID)
    await insertSub(userId, ENDPOINT_1)
    expect(await repo.isKnown(ENDPOINT_1)).toBe(true)
  })

  it('returns false when the endpoint does not exist', async () => {
    expect(await repo.isKnown('https://unknown.example.com')).toBe(false)
  })
})
