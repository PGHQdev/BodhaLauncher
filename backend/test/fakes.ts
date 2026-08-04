import { createApp } from "../src/app"
import type {
  Account,
  BlobStore,
  Clock,
  GoogleIdentity,
  GoogleVerifier,
  RefreshTokenRecord,
  Storage,
} from "../src/ports"

export const JWT_SECRET = "test-jwt-secret"

export interface FakeStorage extends Storage {
  accounts: Map<string, Account>
  refreshTokens: RefreshTokenRecord[]
  devices: Map<string, { accountId: string }>
  entitlements: Map<string, { accountId: string }>
  connectorTokens: Map<string, { accountId: string }>
}

export function fakeStorage(): FakeStorage {
  const storage: FakeStorage = {
    accounts: new Map(),
    refreshTokens: [],
    devices: new Map(),
    entitlements: new Map(),
    connectorTokens: new Map(),

    async findAccountByGoogleSub(googleSub) {
      return [...storage.accounts.values()].find((a) => a.googleSub === googleSub) ?? null
    },
    async getAccount(id) {
      return storage.accounts.get(id) ?? null
    },
    async createAccount(account) {
      storage.accounts.set(account.id, account)
    },
    async setRevenueCatAppUserId(accountId, appUserId) {
      const account = storage.accounts.get(accountId)
      if (account) account.revenueCatAppUserId = appUserId
    },
    async insertRefreshToken(record) {
      storage.refreshTokens.push({ ...record })
    },
    async findRefreshTokenByHash(tokenHash) {
      const record = storage.refreshTokens.find((r) => r.tokenHash === tokenHash)
      return record ? { ...record } : null
    },
    async markRefreshTokenRotated(id) {
      const record = storage.refreshTokens.find((r) => r.id === id)
      if (record) record.status = "rotated"
    },
    async revokeTokenFamily(familyId) {
      for (const record of storage.refreshTokens) {
        if (record.familyId === familyId) record.status = "revoked"
      }
    },
    async deleteAccountData(accountId) {
      storage.accounts.delete(accountId)
      storage.refreshTokens = storage.refreshTokens.filter((r) => r.accountId !== accountId)
      for (const map of [storage.devices, storage.entitlements, storage.connectorTokens]) {
        for (const [key, row] of map) {
          if (row.accountId === accountId) map.delete(key)
        }
      }
    },
  }
  return storage
}

/** Resolves only tokens present in the map; everything else (bad issuer, bad audience, expired, garbage) is null per the port contract. */
export function fakeVerifier(known: Record<string, GoogleIdentity>): GoogleVerifier {
  return {
    async verify(idToken) {
      return known[idToken] ?? null
    },
  }
}

export interface FakeBlobStore extends BlobStore {
  deletedAccounts: string[]
}

export function fakeBlobStore(): FakeBlobStore {
  const store: FakeBlobStore = {
    deletedAccounts: [],
    async deleteAccountBlobs(accountId) {
      store.deletedAccounts.push(accountId)
    },
  }
  return store
}

export interface TestHarness {
  app: ReturnType<typeof createApp>
  storage: FakeStorage
  blobs: FakeBlobStore
  clock: Clock & { advance(ms: number): void }
}

export function makeHarness(
  verifier: GoogleVerifier = fakeVerifier({ "valid-google-token": { sub: "google-sub-1", email: "user@example.com" } }),
): TestHarness {
  const storage = fakeStorage()
  const blobs = fakeBlobStore()
  let nowMs = Date.now()
  const clock = {
    now: () => nowMs,
    advance: (ms: number) => {
      nowMs += ms
    },
  }
  const app = createApp({ verifier, storage, blobs, clock }, { jwtSecret: JWT_SECRET })
  return { app, storage, blobs, clock }
}

export function jsonRequest(
  path: string,
  options: { method?: string; body?: unknown; accessToken?: string } = {},
): Request {
  const headers: Record<string, string> = { "content-type": "application/json" }
  if (options.accessToken) headers.authorization = `Bearer ${options.accessToken}`
  return new Request(`http://backend.test${path}`, {
    method: options.method ?? "POST",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
}

export async function signIn(harness: TestHarness, idToken = "valid-google-token") {
  const response = await harness.app.fetch(
    jsonRequest("/v1/auth/google", { body: { idToken } }),
  )
  if (response.status !== 200) throw new Error(`sign-in failed: ${response.status}`)
  return (await response.json()) as {
    accessToken: string
    refreshToken: string
    expiresIn: number
  }
}
