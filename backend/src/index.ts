import { createApp } from "./app"
import { d1Storage, type D1Database } from "./d1"
import { googleVerifier } from "./google"
import type { BlobStore, Clock } from "./ports"

interface Env {
  DB: D1Database
  JWT_SECRET: string
  GOOGLE_CLIENT_ID: string
}

const systemClock: Clock = { now: () => Date.now() }

// R2 backups are post-MVP (#23); deletion's blob step is a no-op until they exist.
const noopBlobStore: BlobStore = { async deleteAccountBlobs() {} }

let app: ReturnType<typeof createApp> | null = null

export default {
  fetch(request: Request, env: Env): Response | Promise<Response> {
    app ??= createApp(
      {
        verifier: googleVerifier(env.GOOGLE_CLIENT_ID, systemClock),
        storage: d1Storage(env.DB),
        blobs: noopBlobStore,
        clock: systemClock,
      },
      { jwtSecret: env.JWT_SECRET },
    )
    return app.fetch(request)
  },
}
