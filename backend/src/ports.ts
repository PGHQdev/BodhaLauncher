export interface GoogleIdentity {
  sub: string
  email?: string
}

export interface GoogleVerifier {
  /** Resolves the identity only when issuer, signature, audience, and expiry all hold; null otherwise. */
  verify(idToken: string): Promise<GoogleIdentity | null>
}

export interface Account {
  id: string
  googleSub: string
  email?: string
  revenueCatAppUserId?: string
  createdAt: number
}

export type RefreshTokenStatus = "active" | "rotated" | "revoked"

export interface RefreshTokenRecord {
  id: string
  accountId: string
  familyId: string
  tokenHash: string
  status: RefreshTokenStatus
  createdAt: number
  expiresAt: number
}

export interface Storage {
  findAccountByGoogleSub(googleSub: string): Promise<Account | null>
  getAccount(id: string): Promise<Account | null>
  createAccount(account: Account): Promise<void>
  setRevenueCatAppUserId(accountId: string, appUserId: string): Promise<void>
  insertRefreshToken(record: RefreshTokenRecord): Promise<void>
  findRefreshTokenByHash(tokenHash: string): Promise<RefreshTokenRecord | null>
  markRefreshTokenRotated(id: string): Promise<void>
  revokeTokenFamily(familyId: string): Promise<void>
  /** Deletes the account row, device metadata, entitlement mirror, refresh tokens, and connector tokens. */
  deleteAccountData(accountId: string): Promise<void>
}

export interface BlobStore {
  deleteAccountBlobs(accountId: string): Promise<void>
}

export interface Clock {
  /** Epoch milliseconds. */
  now(): number
}

export interface RateLimiter {
  allow(key: string): Promise<boolean>
}

export interface Ports {
  verifier: GoogleVerifier
  storage: Storage
  blobs: BlobStore
  clock: Clock
  rateLimiter?: RateLimiter
}

export interface AppConfig {
  jwtSecret: string
}
