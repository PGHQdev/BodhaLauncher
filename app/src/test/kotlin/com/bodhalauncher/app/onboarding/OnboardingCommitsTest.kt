package com.bodhalauncher.app.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.home.PinStore
import com.bodhalauncher.app.opencheck.OpenCheckRuleStore
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.OpenCheckRule
import com.bodhalauncher.engine.dayKey
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What each step writes on advance, and that skipping writes nothing — asserted
 * per path against the real stores, read back through fresh instances so the
 * writes are the persisted truth rather than in-memory state (ADR 0018).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class OnboardingCommitsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        listOf("home_pins", "open_check_rules", "daily_intention").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun `essentials writes the picks as pins in the order chosen`() {
        commitEssentials(PinStore(context), listOf("b.app", "a.app", "c.app"))
        assertEquals(listOf("b.app", "a.app", "c.app"), PinStore(context).pinned.value)
    }

    @Test
    fun `a revisited essentials re-commits what its screen shows`() {
        // Back into the step, un-pick one, pick another: the pins are the new
        // picks exactly, not the union — the screen never lies about the store.
        commitEssentials(PinStore(context), listOf("a.app", "b.app"))
        commitEssentials(PinStore(context), listOf("a.app", "c.app"))
        assertEquals(listOf("a.app", "c.app"), PinStore(context).pinned.value)
    }

    @Test
    fun `a revisited friction drops the rules it un-picked`() {
        commitFriction(OpenCheckRuleStore(context), listOf("a.app", "b.app"))
        commitFriction(OpenCheckRuleStore(context), listOf("b.app"))
        assertEquals(setOf("b.app"), OpenCheckRuleStore(context).rules.value.keys)
    }

    @Test
    fun `skipping essentials writes no pins`() {
        commitEssentials(PinStore(context), emptyList())
        assertEquals(emptyList<String>(), PinStore(context).pinned.value)
    }

    @Test
    fun `friction writes one always-mode rule per pick and nothing else`() {
        commitFriction(OpenCheckRuleStore(context), listOf("a.app", "b.app"))
        val rules = OpenCheckRuleStore(context).rules.value
        // Bare Always — no threshold, no window, no other mode: the one mode
        // explainable to someone who has not met the feature (#138).
        assertEquals(
            mapOf(
                "a.app" to OpenCheckRule(OpenCheckMode.Always),
                "b.app" to OpenCheckRule(OpenCheckMode.Always),
            ),
            rules,
        )
    }

    @Test
    fun `skipping friction leaves no app checked`() {
        commitFriction(OpenCheckRuleStore(context), emptyList())
        assertEquals(emptyMap<String, OpenCheckRule>(), OpenCheckRuleStore(context).rules.value)
    }

    @Test
    fun `the first intention lands under the 4am day key, not the calendar date`() {
        // Anchored to the real clock so a fresh store's retention prune (which
        // reads now) keeps the record; the boundary math is what is asserted.
        val today = LocalDateTime.now().toLocalDate()
        val at2am = today.atTime(2, 0)
        commitFirstIntention(IntentionStore(context), "Sleep more", at2am)
        val stored = IntentionStore(context).intention.value!!
        assertEquals(today.minusDays(1), stored.dayKey)
        assertEquals(dayKey(at2am), stored.dayKey)
        assertEquals("Sleep more", stored.textOn(today.atTime(3, 45)))
        assertNull(stored.textOn(today.atTime(4, 0)))
    }

    @Test
    fun `skipping the intention leaves the store empty`() {
        assertNull(IntentionStore(context).intention.value)
    }
}
