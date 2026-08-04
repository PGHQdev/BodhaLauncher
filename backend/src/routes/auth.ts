import { Hono } from "hono"
import { z } from "zod"
import type { AppEnv } from "../app"
import type { AppConfig, Ports } from "../ports"
import { hashToken, issueTokenPair, randomId } from "../tokens"
import { invalidRequest, parseBody, unauthorized } from "../validate"

const googleBody = z.object({ idToken: z.string().min(1) }).strict()
const refreshBody = z.object({ refreshToken: z.string().min(1) }).strict()

export function authRoutes(ports: Ports, config: AppConfig): Hono<AppEnv> {
  const { verifier, storage, clock } = ports
  const app = new Hono<AppEnv>()

  app.post("/google", async (c) => {
    const body = await parseBody(c, googleBody)
    if (!body) return c.json(invalidRequest, 400)

    const identity = await verifier.verify(body.idToken)
    if (!identity) return c.json(unauthorized, 401)

    let account = await storage.findAccountByGoogleSub(identity.sub)
    if (!account) {
      account = {
        id: randomId(),
        googleSub: identity.sub,
        email: identity.email,
        createdAt: clock.now(),
      }
      await storage.createAccount(account)
    }

    const pair = await issueTokenPair(storage, clock, config.jwtSecret, account.id)
    return c.json(pair, 200)
  })

  app.post("/refresh", async (c) => {
    const body = await parseBody(c, refreshBody)
    if (!body) return c.json(invalidRequest, 400)

    const record = await storage.findRefreshTokenByHash(await hashToken(body.refreshToken))
    if (!record) return c.json(unauthorized, 401)
    if (record.status === "rotated") {
      // Reuse of a rotated token means it leaked somewhere: kill the whole family.
      await storage.revokeTokenFamily(record.familyId)
      return c.json(unauthorized, 401)
    }
    if (record.status === "revoked" || record.expiresAt <= clock.now()) {
      return c.json(unauthorized, 401)
    }

    await storage.markRefreshTokenRotated(record.id)
    const pair = await issueTokenPair(
      storage,
      clock,
      config.jwtSecret,
      record.accountId,
      record.familyId,
    )
    return c.json(pair, 200)
  })

  app.post("/signout", async (c) => {
    const body = await parseBody(c, refreshBody)
    if (!body) return c.json(invalidRequest, 400)

    const record = await storage.findRefreshTokenByHash(await hashToken(body.refreshToken))
    if (record) await storage.revokeTokenFamily(record.familyId)
    // Unknown tokens get the same answer as known ones: nothing to probe.
    return c.body(null, 204)
  })

  return app
}
