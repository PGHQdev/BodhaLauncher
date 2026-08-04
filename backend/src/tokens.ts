import { sign } from "hono/jwt"
import type { Clock, Storage } from "./ports"

export const ACCESS_TOKEN_TTL_SECONDS = 15 * 60
const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000

export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export function randomId(): string {
  return crypto.randomUUID()
}

function base64url(bytes: Uint8Array): string {
  let binary = ""
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

export async function hashToken(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token))
  return base64url(new Uint8Array(digest))
}

export async function issueTokenPair(
  storage: Storage,
  clock: Clock,
  jwtSecret: string,
  accountId: string,
  familyId?: string,
): Promise<TokenPair> {
  const nowSeconds = Math.floor(clock.now() / 1000)
  const accessToken = await sign(
    { sub: accountId, iat: nowSeconds, exp: nowSeconds + ACCESS_TOKEN_TTL_SECONDS },
    jwtSecret,
  )

  const secret = crypto.getRandomValues(new Uint8Array(32))
  const refreshToken = base64url(secret)
  await storage.insertRefreshToken({
    id: randomId(),
    accountId,
    familyId: familyId ?? randomId(),
    tokenHash: await hashToken(refreshToken),
    status: "active",
    createdAt: clock.now(),
    expiresAt: clock.now() + REFRESH_TOKEN_TTL_MS,
  })

  return { accessToken, refreshToken, expiresIn: ACCESS_TOKEN_TTL_SECONDS }
}
