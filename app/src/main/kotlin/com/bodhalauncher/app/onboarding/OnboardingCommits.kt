package com.bodhalauncher.app.onboarding

import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.opencheck.OpenCheckRuleStore
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.OpenCheckRule
import java.time.LocalDateTime

/**
 * What each onboarding step writes on advance (ADR 0018: each step commits to
 * its real store, no draft layer). The skip path calls none of these — skipping
 * writes nothing anywhere, which the tests assert per path.
 */

/**
 * Essentials (#137): the picks become pins, in the order chosen. Reconciled
 * rather than appended, so a step revisited over back re-commits what its
 * screen shows: an un-picked pin is unpinned. During onboarding this step is
 * the only writer, so nothing else's pins are at stake.
 */
fun commitEssentials(pinStore: PinStore, picks: List<String>) {
    pinStore.pinned.value.filterNot { it in picks }.forEach(pinStore::unpin)
    picks.forEach(pinStore::pin)
}

/**
 * Friction (#138): one always-mode rule per pick — the one mode explainable to
 * someone who has not met the feature. No entitlement gate is consulted: free
 * Open Check caps at three, the picker caps at three, and a paywall in step
 * three is forbidden. Reconciled for the same reason as [commitEssentials]:
 * this step is the only rule writer while the flow runs.
 */
fun commitFriction(ruleStore: OpenCheckRuleStore, picks: List<String>) {
    ruleStore.rules.value.keys.filterNot { it in picks }.forEach(ruleStore::remove)
    picks.forEach { ruleStore.set(it, OpenCheckRule(OpenCheckMode.Always)) }
}

/**
 * First intention (#139): filed under the day key the 4am boundary computes,
 * so a 2am finish belongs to the previous day and expires at the coming 4am.
 */
fun commitFirstIntention(intentionStore: IntentionStore, text: String, now: LocalDateTime) =
    intentionStore.set(text, now)
