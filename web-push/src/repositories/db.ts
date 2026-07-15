import { Kysely, PostgresDialect } from 'kysely'
import { Pool } from 'pg'
import type { Generated } from 'kysely'

export interface UsersTable {
  id: Generated<bigint>
  email: string | null
  firstname: string | null
  lastname: string | null
  oidcid: string | null
}

export interface PushsubscriptionTable {
  id: Generated<bigint>
  auth: string
  endpoint: string
  p256dh: string
  user_id: bigint
}

export interface Database {
  users: UsersTable
  pushsubscription: PushsubscriptionTable
}

export interface DbConfig {
  host: string
  port: number
  database: string
  user: string
  password: string
}

export function createDb(config: DbConfig): Kysely<Database> {
  return new Kysely<Database>({
    dialect: new PostgresDialect({
      pool: new Pool(config),
    }),
  })
}
