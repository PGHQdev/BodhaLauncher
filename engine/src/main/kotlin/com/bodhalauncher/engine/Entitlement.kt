package com.bodhalauncher.engine

import java.time.Instant

/**
 * The locally cached entitlement fact (#22). The cache is authoritative: a stale
 * or never-fetched snapshot never downgrades mid-session, so billing outages
 * cannot touch the launcher. A fresh install is free.
 */
data class EntitlementSnapshot(
    val proActive: Boolean = false,
    val fetchedAt: Instant? = null,
)

/**
 * A Pro-boundary question (ADR 0005). Free features have no request type here —
 * they never consult the gate at all.
 */
sealed interface GatedRequest {
    data class AddOpenCheckRule(val existingRules: Int) : GatedRequest
    data object AwarenessHistory : GatedRequest
    data object FocusScheduling : GatedRequest
    data object Reflection : GatedRequest
    data object AdvancedThemes : GatedRequest
    data object EncryptedSync : GatedRequest
    data object ExternalConnectors : GatedRequest
}

/** The calm boundary copy, as data so tone is testable (#22). */
data class ProBoundary(val explanation: String)

sealed interface GateDecision {
    data object Allowed : GateDecision

    /** Allowed up to [limit]; the caller applies the cap and may show [boundary]. */
    data class Capped(val limit: Int, val boundary: ProBoundary) : GateDecision

    data class Locked(val boundary: ProBoundary) : GateDecision
}

const val FREE_OPEN_CHECK_RULES = 3
const val FREE_AWARENESS_DAYS = 7

/** Resolves a gated request against the cached snapshot (#22, caps per ADR 0005). */
fun resolveEntitlement(snapshot: EntitlementSnapshot, request: GatedRequest): GateDecision {
    if (snapshot.proActive) return GateDecision.Allowed
    return when (request) {
        is GatedRequest.AddOpenCheckRule ->
            if (request.existingRules < FREE_OPEN_CHECK_RULES) GateDecision.Allowed
            else GateDecision.Capped(
                FREE_OPEN_CHECK_RULES,
                ProBoundary("Three rules come with Bodha. Unlimited rules are part of Pro."),
            )

        GatedRequest.AwarenessHistory -> GateDecision.Capped(
            FREE_AWARENESS_DAYS,
            ProBoundary("The past seven days come with Bodha. Your full history is part of Pro."),
        )

        GatedRequest.FocusScheduling -> locked("Scheduled and recurring Focus is part of Pro.")
        GatedRequest.Reflection -> locked("Reflection is part of Pro.")
        GatedRequest.AdvancedThemes -> locked("The full accent set is part of Pro.")
        GatedRequest.EncryptedSync -> locked("Encrypted sync and backup are part of Pro.")
        GatedRequest.ExternalConnectors -> locked("External connectors are part of Pro.")
    }
}

private fun locked(explanation: String) = GateDecision.Locked(ProBoundary(explanation))

/**
 * Resolves an Open Check rule write (#71): only creation consults the gate —
 * editing an existing rule is always allowed, so rules keep working and stay
 * editable if entitlement lapses.
 */
fun resolveOpenCheckRuleWrite(
    snapshot: EntitlementSnapshot,
    existingRules: Int,
    creating: Boolean,
): GateDecision =
    if (creating) resolveEntitlement(snapshot, GatedRequest.AddOpenCheckRule(existingRules))
    else GateDecision.Allowed
