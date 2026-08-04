import { Hono } from "hono"
import { verify } from "hono/jwt"
import { z } from "zod"
import type { AppEnv } from "../app"
import type { AppConfig, Ports } from "../ports"
import { invalidRequest, parseBody, unauthorized } from "../validate"

const revenueCatBody = z.object({ appUserId: z.string().min(1).max(200) }).strict()

export function accountRoutes(ports: Ports, config: AppConfig): Hono<AppEnv> {
  const { storage, blobs } = ports
  const app = new Hono<AppEnv>()

  app.use("*", async (c, next) => {
    const header = c.req.header("authorization")
    if (!header?.startsWith("Bearer ")) return c.json(unauthorized, 401)
    let sub: unknown
    try {
      sub = (await verify(header.slice("Bearer ".length), config.jwtSecret, "HS256")).sub
    } catch {
      return c.json(unauthorized, 401)
    }
    if (typeof sub !== "string" || !(await storage.getAccount(sub))) {
      return c.json(unauthorized, 401)
    }
    c.set("accountId", sub)
    await next()
  })

  app.delete("/", async (c) => {
    const accountId = c.get("accountId")
    await blobs.deleteAccountBlobs(accountId)
    await storage.deleteAccountData(accountId)
    return c.body(null, 204)
  })

  app.post("/revenuecat", async (c) => {
    const body = await parseBody(c, revenueCatBody)
    if (!body) return c.json(invalidRequest, 400)
    await storage.setRevenueCatAppUserId(c.get("accountId"), body.appUserId)
    return c.body(null, 204)
  })

  return app
}
