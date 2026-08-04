import { Hono } from "hono"
import type { AppConfig, Ports } from "./ports"
import { authRoutes } from "./routes/auth"
import { accountRoutes } from "./routes/account"

export interface AppVariables {
  accountId: string
}

export type AppEnv = { Variables: AppVariables }

export function createApp(ports: Ports, config: AppConfig): Hono<AppEnv> {
  const app = new Hono<AppEnv>()

  app.use("/v1/*", async (c, next) => {
    if (ports.rateLimiter) {
      const key = c.req.header("cf-connecting-ip") ?? "unknown"
      if (!(await ports.rateLimiter.allow(key))) {
        return c.json({ error: "rate_limited" }, 429)
      }
    }
    await next()
  })

  app.route("/v1/auth", authRoutes(ports, config))
  app.route("/v1/account", accountRoutes(ports, config))

  app.notFound((c) => c.json({ error: "not_found" }, 404))
  // Bodies and tokens are never logged (#24); errors surface as an opaque 500.
  app.onError((_err, c) => c.json({ error: "internal" }, 500))

  return app
}
