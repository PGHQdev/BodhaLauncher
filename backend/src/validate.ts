import type { Context } from "hono"
import type { z } from "zod"

/**
 * Strictly parses the JSON body against the schema. Any failure (unparseable JSON,
 * wrong shape, unknown keys) yields null; callers respond 400 with a generic body —
 * request contents are never echoed or logged.
 */
export async function parseBody<T extends z.ZodType>(
  c: Context,
  schema: T,
): Promise<z.infer<T> | null> {
  let raw: unknown
  try {
    raw = await c.req.json()
  } catch {
    return null
  }
  const result = schema.safeParse(raw)
  return result.success ? result.data : null
}

export const invalidRequest = { error: "invalid_request" } as const
export const unauthorized = { error: "unauthorized" } as const
