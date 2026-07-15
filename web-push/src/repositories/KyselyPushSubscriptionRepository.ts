import { Kysely } from 'kysely'
import type { Database } from './db.js'
import type { IPushSubscriptionRepository, PushSubscription } from './IPushSubscriptionRepository.js'
import { createDb, type DbConfig } from './db.js'

export class KyselyPushSubscriptionRepository implements IPushSubscriptionRepository {
  private readonly db: Kysely<Database>

  constructor(configOrDb: DbConfig | Kysely<Database>) {
    this.db = configOrDb instanceof Kysely ? configOrDb : createDb(configOrDb)
  }

  async findByEndpoint(endpoint: string): Promise<PushSubscription | undefined> {
    const row = await this.db
      .selectFrom('pushsubscription')
      .selectAll()
      .where('endpoint', '=', endpoint)
      .executeTakeFirst()

    return row ? this.toModel(row) : undefined
  }

  async findByKeycloakId(keycloakId: string): Promise<PushSubscription[]> {
    const rows = await this.db
      .selectFrom('pushsubscription')
      .innerJoin('users', 'users.id', 'pushsubscription.user_id')
      .where('users.oidcid', '=', keycloakId)
      .select([
        'pushsubscription.id',
        'pushsubscription.endpoint',
        'pushsubscription.auth',
        'pushsubscription.p256dh',
        'pushsubscription.user_id',
      ])
      .execute()

    return rows.map(this.toModel)
  }

  async save(keycloakId: string, endpoint: string, auth: string, p256dh: string): Promise<void> {
    const user = await this.db
      .selectFrom('users')
      .select('id')
      .where('oidcid', '=', keycloakId)
      .executeTakeFirst()

    if (!user) return

    await this.db.transaction().execute(async (trx) => {
      await trx.deleteFrom('pushsubscription').where('endpoint', '=', endpoint).execute()
      await trx
        .insertInto('pushsubscription')
        .values({ endpoint, auth, p256dh, user_id: user.id })
        .execute()
    })
  }

  async deleteByEndpoint(endpoint: string): Promise<void> {
    await this.db.deleteFrom('pushsubscription').where('endpoint', '=', endpoint).execute()
  }

  async isKnown(endpoint: string): Promise<boolean> {
    const row = await this.db
      .selectFrom('pushsubscription')
      .select('id')
      .where('endpoint', '=', endpoint)
      .executeTakeFirst()
    return row !== undefined
  }

  private toModel(row: {
    id: bigint
    endpoint: string
    auth: string
    p256dh: string
    user_id: bigint
  }): PushSubscription {
    return {
      id: row.id,
      endpoint: row.endpoint,
      auth: row.auth,
      p256dh: row.p256dh,
      userId: row.user_id,
    }
  }
}
