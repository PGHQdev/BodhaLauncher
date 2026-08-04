package com.bodhalauncher.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntitlementGateTest {

    private val free = EntitlementSnapshot()
    private val pro = EntitlementSnapshot(proActive = true, fetchedAt = Instant.parse("2026-08-01T00:00:00Z"))

    @Test
    fun `a fresh install defaults to free`() {
        assertEquals(false, EntitlementSnapshot().proActive)
    }

    @Test
    fun `pro allows every gated request`() {
        val requests = listOf(
            GatedRequest.AddOpenCheckRule(existingRules = 99),
            GatedRequest.AwarenessHistory,
            GatedRequest.FocusScheduling,
            GatedRequest.Reflection,
            GatedRequest.AdvancedThemes,
            GatedRequest.EncryptedSync,
            GatedRequest.ExternalConnectors,
        )
        for (request in requests) {
            assertEquals(GateDecision.Allowed, resolveEntitlement(pro, request), "request: $request")
        }
    }

    @Test
    fun `free adds open check rules below the cap`() {
        assertEquals(GateDecision.Allowed, resolveEntitlement(free, GatedRequest.AddOpenCheckRule(existingRules = 2)))
    }

    @Test
    fun `free hits the open check cap at three rules`() {
        val decision = resolveEntitlement(free, GatedRequest.AddOpenCheckRule(existingRules = 3))

        val capped = assertIs<GateDecision.Capped>(decision)
        assertEquals(FREE_OPEN_CHECK_RULES, capped.limit)
        assertTrue(capped.boundary.explanation.isNotBlank())
    }

    @Test
    fun `free awareness history caps at seven days`() {
        val capped = assertIs<GateDecision.Capped>(resolveEntitlement(free, GatedRequest.AwarenessHistory))

        assertEquals(FREE_AWARENESS_DAYS, capped.limit)
    }

    @Test
    fun `free locks the pro-only features with an explanation`() {
        val proOnly = listOf(
            GatedRequest.FocusScheduling,
            GatedRequest.Reflection,
            GatedRequest.AdvancedThemes,
            GatedRequest.EncryptedSync,
            GatedRequest.ExternalConnectors,
        )
        for (request in proOnly) {
            val locked = assertIs<GateDecision.Locked>(resolveEntitlement(free, request), "request: $request")
            assertTrue(locked.boundary.explanation.isNotBlank())
        }
    }

    @Test
    fun `a stale pro snapshot never downgrades`() {
        val stale = EntitlementSnapshot(proActive = true, fetchedAt = Instant.EPOCH)

        assertEquals(GateDecision.Allowed, resolveEntitlement(stale, GatedRequest.ExternalConnectors))
    }

    /** Adds through the gate the way the adapter does: allowed means the rule lands. */
    private fun tryAdd(rules: MutableMap<String, OpenCheckMode>, id: String, snapshot: EntitlementSnapshot): GateDecision {
        val decision = resolveEntitlement(snapshot, GatedRequest.AddOpenCheckRule(rules.size))
        if (decision == GateDecision.Allowed) rules[id] = OpenCheckMode.Always
        return decision
    }

    @Test
    fun `a free user walks the add-rule path to the cap`() {
        val rules = mutableMapOf<String, OpenCheckMode>()

        repeat(3) { assertEquals(GateDecision.Allowed, tryAdd(rules, "app$it", free)) }
        val fourth = tryAdd(rules, "app3", free)

        val capped = assertIs<GateDecision.Capped>(fourth)
        assertEquals("Three rules come with Bodha. Unlimited rules are part of Pro.", capped.boundary.explanation)
        assertEquals(setOf("app0", "app1", "app2"), rules.keys)
    }

    @Test
    fun `removing a rule frees a slot for a free user`() {
        val rules = mutableMapOf<String, OpenCheckMode>()
        repeat(3) { tryAdd(rules, "app$it", free) }

        rules.remove("app0")

        assertEquals(GateDecision.Allowed, tryAdd(rules, "app3", free))
    }

    @Test
    fun `an entitlement lapse caps new rules but never touches existing ones`() {
        val rules = mutableMapOf<String, OpenCheckMode>()
        repeat(5) { tryAdd(rules, "app$it", pro) }

        val afterLapse = tryAdd(rules, "app5", free)

        assertIs<GateDecision.Capped>(afterLapse)
        assertEquals(5, rules.size)
    }

    @Test
    fun `boundary explanations carry no urgency`() {
        val capped = assertIs<GateDecision.Capped>(resolveEntitlement(free, GatedRequest.AddOpenCheckRule(3)))

        for (banned in listOf("now", "hurry", "only", "!")) {
            assertEquals(false, capped.boundary.explanation.contains(banned, ignoreCase = true), "found: $banned")
        }
    }
}
