import type { Account, RefreshTokenRecord, Storage } from "./ports"

// Minimal structural D1 types for the calls this adapter makes; avoids pulling
// @cloudflare/workers-types, whose globals clash with Bun's in one tsconfig.
export interface D1Database {
  prepare(query: string): D1PreparedStatement
  batch(statements: D1PreparedStatement[]): Promise<unknown>
}

export interface D1PreparedStatement {
  bind(...values: unknown[]): D1PreparedStatement
  first<T>(): Promise<T | null>
  run(): Promise<unknown>
}

interface AccountRow {
  id: string
  google_sub: string
  email: string | null
  revenuecat_app_user_id: string | null
  created_at: number
}

interface RefreshTokenRow {
  id: string
  account_id: string
  family_id: string
  token_hash: string
  status: RefreshTokenRecord["status"]
  created_at: number
  expires_at: number
}

function toAccount(row: AccountRow): Account {
  return {
    id: row.id,
    googleSub: row.google_sub,
    email: row.email ?? undefined,
    revenueCatAppUserId: row.revenuecat_app_user_id ?? undefined,
    createdAt: row.created_at,
  }
}

function toRefreshToken(row: RefreshTokenRow): RefreshTokenRecord {
  return {
    id: row.id,
    accountId: row.account_id,
    familyId: row.family_id,
    tokenHash: row.token_hash,
    status: row.status,
    createdAt: row.created_at,
    expiresAt: row.expires_at,
  }
}

export function d1Storage(db: D1Database): Storage {
  return {
    async findAccountByGoogleSub(googleSub) {
      const row = await db
        .prepare("SELECT * FROM accounts WHERE google_sub = ?")
        .bind(googleSub)
        .first<AccountRow>()
      return row ? toAccount(row) : null
    },

    async getAccount(id) {
      const row = await db.prepare("SELECT * FROM accounts WHERE id = ?").bind(id).first<AccountRow>()
      return row ? toAccount(row) : null
    },

    async createAccount(account) {
      await db
        .prepare("INSERT INTO accounts (id, google_sub, email, created_at) VALUES (?, ?, ?, ?)")
        .bind(account.id, account.googleSub, account.email ?? null, account.createdAt)
        .run()
    },

    async setRevenueCatAppUserId(accountId, appUserId) {
      await db
        .prepare("UPDATE accounts SET revenuecat_app_user_id = ? WHERE id = ?")
        .bind(appUserId, accountId)
        .run()
    },

    async insertRefreshToken(record) {
      await db
        .prepare(
          "INSERT INTO refresh_tokens (id, account_id, family_id, token_hash, status, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        )
        .bind(
          record.id,
          record.accountId,
          record.familyId,
          record.tokenHash,
          record.status,
          record.createdAt,
          record.expiresAt,
        )
        .run()
    },

    async findRefreshTokenByHash(tokenHash) {
      const row = await db
        .prepare("SELECT * FROM refresh_tokens WHERE token_hash = ?")
        .bind(tokenHash)
        .first<RefreshTokenRow>()
      return row ? toRefreshToken(row) : null
    },

    async markRefreshTokenRotated(id) {
      await db.prepare("UPDATE refresh_tokens SET status = 'rotated' WHERE id = ?").bind(id).run()
    },

    async revokeTokenFamily(familyId) {
      await db
        .prepare("UPDATE refresh_tokens SET status = 'revoked' WHERE family_id = ?")
        .bind(familyId)
        .run()
    },

    async deleteAccountData(accountId) {
      await db.batch([
        db.prepare("DELETE FROM refresh_tokens WHERE account_id = ?").bind(accountId),
        db.prepare("DELETE FROM connector_tokens WHERE account_id = ?").bind(accountId),
        db.prepare("DELETE FROM entitlements WHERE account_id = ?").bind(accountId),
        db.prepare("DELETE FROM devices WHERE account_id = ?").bind(accountId),
        db.prepare("DELETE FROM accounts WHERE id = ?").bind(accountId),
      ])
    },
  }
}
