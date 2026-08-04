import type { Clock, GoogleIdentity, GoogleVerifier } from "./ports"

const JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
const VALID_ISSUERS = ["https://accounts.google.com", "accounts.google.com"]

interface Jwk {
  kid: string
  kty: string
  n: string
  e: string
}

function base64urlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/")
  const binary = atob(padded + "=".repeat((4 - (padded.length % 4)) % 4))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

function decodeJson(segment: string): Record<string, unknown> {
  return JSON.parse(new TextDecoder().decode(base64urlToBytes(segment)))
}

/**
 * Production adapter for the verifier port: validates a Google ID token's issuer,
 * signature (against Google's JWKS), audience, and expiry. Untested edge by spec —
 * the port is faked in tests.
 */
export function googleVerifier(clientId: string, clock: Clock): GoogleVerifier {
  let jwks: Jwk[] | null = null
  let jwksFetchedAt = 0

  async function getKeys(): Promise<Jwk[]> {
    if (!jwks || clock.now() - jwksFetchedAt > 60 * 60 * 1000) {
      const response = await fetch(JWKS_URL)
      if (!response.ok) throw new Error("jwks fetch failed")
      jwks = ((await response.json()) as { keys: Jwk[] }).keys
      jwksFetchedAt = clock.now()
    }
    return jwks
  }

  return {
    async verify(idToken): Promise<GoogleIdentity | null> {
      try {
        const segments = idToken.split(".")
        if (segments.length !== 3) return null
        const [headerSegment, payloadSegment, signatureSegment] = segments as [
          string,
          string,
          string,
        ]

        const header = decodeJson(headerSegment)
        if (header.alg !== "RS256") return null
        const jwk = (await getKeys()).find((key) => key.kid === header.kid)
        if (!jwk) return null

        const key = await crypto.subtle.importKey(
          "jwk",
          jwk,
          { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
          false,
          ["verify"],
        )
        const valid = await crypto.subtle.verify(
          "RSASSA-PKCS1-v1_5",
          key,
          base64urlToBytes(signatureSegment),
          new TextEncoder().encode(`${headerSegment}.${payloadSegment}`),
        )
        if (!valid) return null

        const payload = decodeJson(payloadSegment)
        if (!VALID_ISSUERS.includes(payload.iss as string)) return null
        if (payload.aud !== clientId) return null
        if (typeof payload.exp !== "number" || payload.exp * 1000 <= clock.now()) return null
        if (typeof payload.sub !== "string") return null

        return {
          sub: payload.sub,
          email: typeof payload.email === "string" ? payload.email : undefined,
        }
      } catch {
        return null
      }
    },
  }
}
