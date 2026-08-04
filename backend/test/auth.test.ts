import { describe, expect, test } from "bun:test"
import { sign, verify } from "hono/jwt"
import { JWT_SECRET, fakeVerifier, jsonRequest, makeHarness, signIn } from "./fakes"

describe("POST /v1/auth/google", () => {
  test("valid token creates the account and issues an access/refresh pair", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)

    expect(pair.expiresIn).toBe(15 * 60)
    expect(pair.refreshToken.length).toBeGreaterThan(20)

    const payload = await verify(pair.accessToken, JWT_SECRET, "HS256")
    const account = [...harness.storage.accounts.values()][0]!
    expect(account.googleSub).toBe("google-sub-1")
    expect(payload.sub).toBe(account.id)

    expect(harness.storage.refreshTokens).toHaveLength(1)
    expect(harness.storage.refreshTokens[0]!.status).toBe("active")
  })

  test("second sign-in resolves the same account", async () => {
    const harness = makeHarness()
    await signIn(harness)
    await signIn(harness)
    expect(harness.storage.accounts.size).toBe(1)
    expect(harness.storage.refreshTokens).toHaveLength(2)
  })

  test.each(["bad-issuer-token", "bad-audience-token", "expired-token"])(
    "token rejected by the verifier (%s) is 401 and creates nothing",
    async (idToken) => {
      const harness = makeHarness(fakeVerifier({}))
      const response = await harness.app.fetch(jsonRequest("/v1/auth/google", { body: { idToken } }))
      expect(response.status).toBe(401)
      expect(harness.storage.accounts.size).toBe(0)
      expect(harness.storage.refreshTokens).toHaveLength(0)
    },
  )
})

describe("POST /v1/auth/refresh", () => {
  test("rotates: new pair issued, used token marked rotated", async () => {
    const harness = makeHarness()
    const first = await signIn(harness)

    const response = await harness.app.fetch(
      jsonRequest("/v1/auth/refresh", { body: { refreshToken: first.refreshToken } }),
    )
    expect(response.status).toBe(200)
    const second = (await response.json()) as { accessToken: string; refreshToken: string }
    expect(second.refreshToken).not.toBe(first.refreshToken)

    const statuses = harness.storage.refreshTokens.map((r) => r.status)
    expect(statuses).toEqual(["rotated", "active"])
    const familyIds = new Set(harness.storage.refreshTokens.map((r) => r.familyId))
    expect(familyIds.size).toBe(1)
  })

  test("reuse of a rotated token revokes the whole family", async () => {
    const harness = makeHarness()
    const first = await signIn(harness)
    await harness.app.fetch(
      jsonRequest("/v1/auth/refresh", { body: { refreshToken: first.refreshToken } }),
    )

    const reuse = await harness.app.fetch(
      jsonRequest("/v1/auth/refresh", { body: { refreshToken: first.refreshToken } }),
    )
    expect(reuse.status).toBe(401)
    expect(harness.storage.refreshTokens.every((r) => r.status === "revoked")).toBe(true)
  })

  test("unknown token is 401", async () => {
    const harness = makeHarness()
    const response = await harness.app.fetch(
      jsonRequest("/v1/auth/refresh", { body: { refreshToken: "never-issued" } }),
    )
    expect(response.status).toBe(401)
  })

  test("expired refresh token is 401", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)
    harness.clock.advance(31 * 24 * 60 * 60 * 1000)
    const response = await harness.app.fetch(
      jsonRequest("/v1/auth/refresh", { body: { refreshToken: pair.refreshToken } }),
    )
    expect(response.status).toBe(401)
  })
})

describe("POST /v1/auth/signout", () => {
  test("revokes the presented token's family", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)

    const response = await harness.app.fetch(
      jsonRequest("/v1/auth/signout", { body: { refreshToken: pair.refreshToken } }),
    )
    expect(response.status).toBe(204)
    expect(harness.storage.refreshTokens.every((r) => r.status === "revoked")).toBe(true)

    const refresh = await harness.app.fetch(
      jsonRequest("/v1/auth/refresh", { body: { refreshToken: pair.refreshToken } }),
    )
    expect(refresh.status).toBe(401)
  })

  test("unknown token still gets 204", async () => {
    const harness = makeHarness()
    const response = await harness.app.fetch(
      jsonRequest("/v1/auth/signout", { body: { refreshToken: "never-issued" } }),
    )
    expect(response.status).toBe(204)
  })
})

describe("validation", () => {
  test.each([
    ["missing field", {}],
    ["wrong type", { idToken: 42 }],
    ["unknown key", { idToken: "x", extra: true }],
  ])("malformed body (%s) is 400 and never echoed", async (_name, body) => {
    const harness = makeHarness()
    const response = await harness.app.fetch(jsonRequest("/v1/auth/google", { body }))
    expect(response.status).toBe(400)
    const text = await response.text()
    expect(text).toBe(JSON.stringify({ error: "invalid_request" }))
  })

  test("unparseable JSON is 400", async () => {
    const harness = makeHarness()
    const response = await harness.app.fetch(
      new Request("http://backend.test/v1/auth/google", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: "not-json{{",
      }),
    )
    expect(response.status).toBe(400)
  })
})

describe("rate limiting", () => {
  test("denying limiter yields 429 on any /v1 route", async () => {
    const harness = makeHarness()
    const storage = harness.storage
    const app = (await import("../src/app")).createApp(
      {
        verifier: fakeVerifier({}),
        storage,
        blobs: harness.blobs,
        clock: harness.clock,
        rateLimiter: { allow: async () => false },
      },
      { jwtSecret: JWT_SECRET },
    )
    const response = await app.fetch(jsonRequest("/v1/auth/google", { body: { idToken: "x" } }))
    expect(response.status).toBe(429)
  })
})

describe("access token expiry", () => {
  test("expired access token is 401 on account routes", async () => {
    const harness = makeHarness()
    await signIn(harness)
    const account = [...harness.storage.accounts.values()][0]!
    const past = Math.floor(Date.now() / 1000) - 60
    const expired = await sign({ sub: account.id, iat: past - 900, exp: past }, JWT_SECRET)
    const response = await harness.app.fetch(
      jsonRequest("/v1/account", { method: "DELETE", accessToken: expired }),
    )
    expect(response.status).toBe(401)
  })
})
