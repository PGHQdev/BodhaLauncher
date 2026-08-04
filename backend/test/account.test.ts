import { describe, expect, test } from "bun:test"
import { jsonRequest, makeHarness, signIn } from "./fakes"

describe("DELETE /v1/account", () => {
  test("cascades over account, devices, entitlements, refresh tokens, connector tokens, and blobs", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)
    const account = [...harness.storage.accounts.values()][0]!
    harness.storage.devices.set("d1", { accountId: account.id })
    harness.storage.entitlements.set(account.id, { accountId: account.id })
    harness.storage.connectorTokens.set("c1", { accountId: account.id })

    const response = await harness.app.fetch(
      jsonRequest("/v1/account", { method: "DELETE", accessToken: pair.accessToken }),
    )
    expect(response.status).toBe(204)

    expect(harness.storage.accounts.size).toBe(0)
    expect(harness.storage.devices.size).toBe(0)
    expect(harness.storage.entitlements.size).toBe(0)
    expect(harness.storage.connectorTokens.size).toBe(0)
    expect(harness.storage.refreshTokens).toHaveLength(0)
    expect(harness.blobs.deletedAccounts).toEqual([account.id])

    const refresh = await harness.app.fetch(
      jsonRequest("/v1/auth/refresh", { body: { refreshToken: pair.refreshToken } }),
    )
    expect(refresh.status).toBe(401)
  })

  test("does not touch other accounts", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)
    harness.storage.accounts.set("other", {
      id: "other",
      googleSub: "google-sub-2",
      createdAt: 0,
    })
    harness.storage.devices.set("d-other", { accountId: "other" })

    const response = await harness.app.fetch(
      jsonRequest("/v1/account", { method: "DELETE", accessToken: pair.accessToken }),
    )
    expect(response.status).toBe(204)
    expect(harness.storage.accounts.has("other")).toBe(true)
    expect(harness.storage.devices.has("d-other")).toBe(true)
  })
})

describe("POST /v1/account/revenuecat", () => {
  test("associates the RevenueCat app user id with the account", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)

    const response = await harness.app.fetch(
      jsonRequest("/v1/account/revenuecat", {
        body: { appUserId: "rc-app-user-1" },
        accessToken: pair.accessToken,
      }),
    )
    expect(response.status).toBe(204)
    const account = [...harness.storage.accounts.values()][0]!
    expect(account.revenueCatAppUserId).toBe("rc-app-user-1")
  })

  test("malformed body is 400", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)
    const response = await harness.app.fetch(
      jsonRequest("/v1/account/revenuecat", { body: { appUserId: 7 }, accessToken: pair.accessToken }),
    )
    expect(response.status).toBe(400)
  })
})

describe("account route authentication", () => {
  test.each([
    ["no header", undefined],
    ["garbage token", "not-a-jwt"],
  ])("unauthenticated request (%s) is 401", async (_name, accessToken) => {
    const harness = makeHarness()
    for (const request of [
      jsonRequest("/v1/account", { method: "DELETE", accessToken }),
      jsonRequest("/v1/account/revenuecat", { body: { appUserId: "rc" }, accessToken }),
    ]) {
      const response = await harness.app.fetch(request)
      expect(response.status).toBe(401)
    }
  })

  test("valid token for a deleted account is 401", async () => {
    const harness = makeHarness()
    const pair = await signIn(harness)
    await harness.app.fetch(
      jsonRequest("/v1/account", { method: "DELETE", accessToken: pair.accessToken }),
    )
    const again = await harness.app.fetch(
      jsonRequest("/v1/account", { method: "DELETE", accessToken: pair.accessToken }),
    )
    expect(again.status).toBe(401)
  })
})
