# Bodha backend

v1 API for the few things a device can't do alone (#21, #23): Google sign-in exchange, token refresh/sign-out, cascading account deletion, RevenueCat association. Cloudflare Workers + Hono + D1.

## Dependencies

- `hono` — router and Worker fetch adapter; `hono/jwt` also signs/verifies access tokens, so no separate JWT library (jose) is needed.
- `zod` — strict schema validation at the edge of every route, with unknown-key rejection.

## Layout

- `src/app.ts` — app factory; the fetch handler is the single test seam. All external dependencies (Google verifier, storage, blobs, clock, rate limiter) are ports injected here (`src/ports.ts`).
- `src/routes/` — auth and account routes.
- `src/d1.ts`, `src/google.ts`, `src/index.ts` — production adapters and Worker entry (untested edges by spec).
- `migrations/0001_init.sql` — D1 schema. The exclusions list in its header is a contract.
- `test/` — route-level tests against faked ports.

## Commands

```sh
bun install
bun test
bun run typecheck
```

Deploy needs the real D1 `database_id` in `wrangler.jsonc` (placeholder committed) and secrets `JWT_SECRET`, `GOOGLE_CLIENT_ID` via `wrangler secret put`. Apply migrations with `bunx wrangler d1 migrations apply bodha`.

## Conventions

- Malformed bodies → `400 {"error":"invalid_request"}`; request contents are never echoed or logged.
- Access tokens: HS256 JWT, 15 min. Refresh tokens: opaque, stored as SHA-256 hashes, family-based rotation; reuse of a rotated token revokes the family (30-day expiry).
