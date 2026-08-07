package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DigestTest {

    private fun signals(
        conversation: Boolean? = false,
        messagingStyle: Boolean = false,
        category: String? = null,
        importance: Int = IMPORTANCE_DEFAULT,
        alerted: Boolean = true,
    ) = NotificationSignals(conversation, messagingStyle, category, importance, alerted)

    // Classification, one signal at a time (#161).

    @Test
    fun `a conversation lands in People even at low importance`() {
        val placement = classifyNotification(signals(conversation = true, importance = 1))
        assertEquals(DigestSection.People, placement.section)
        assertTrue("conversation" in placement.signal)
    }

    @Test
    fun `pre-API-31, messaging style is the People fallback`() {
        val placement = classifyNotification(signals(conversation = null, messagingStyle = true))
        assertEquals(DigestSection.People, placement.section)
        assertTrue("fallback" in placement.signal)
    }

    @Test
    fun `where the ranking answers, messaging style alone does not reach People`() {
        val placement = classifyNotification(signals(conversation = false, messagingStyle = true))
        assertEquals(DigestSection.Updates, placement.section)
    }

    @Test
    fun `a time-sensitive category at default importance lands in Time-sensitive`() {
        for (category in listOf("alarm", "call", "event", "reminder", "navigation", "transport")) {
            val placement = classifyNotification(signals(category = category))
            assertEquals(DigestSection.TimeSensitive, placement.section, category)
            assertTrue(category in placement.signal)
        }
    }

    @Test
    fun `a time-sensitive category demoted below default goes Silent`() {
        val placement = classifyNotification(signals(category = "alarm", importance = 2))
        assertEquals(DigestSection.Silent, placement.section)
    }

    @Test
    fun `never audibly alerted goes Silent`() {
        val placement = classifyNotification(signals(alerted = false))
        assertEquals(DigestSection.Silent, placement.section)
        assertEquals("never audibly alerted", placement.signal)
    }

    @Test
    fun `an update category names its signal`() {
        val placement = classifyNotification(signals(category = "promo"))
        assertEquals(DigestSection.Updates, placement.section)
        assertTrue("promo" in placement.signal)
    }

    @Test
    fun `a notification matching no named signal falls to Updates, the catch-all`() {
        val placement = classifyNotification(signals())
        assertEquals(DigestSection.Updates, placement.section)
        assertTrue("catch-all" in placement.signal)
    }

    @Test
    fun `the function is total - every combination lands in exactly one section`() {
        for (conversation in listOf(true, false, null))
            for (messaging in listOf(true, false))
                for (category in listOf(null, "alarm", "promo", "email"))
                    for (importance in 0..5)
                        for (alerted in listOf(true, false)) {
                            // classify throws nothing and always returns a section.
                            classifyNotification(
                                signals(conversation, messaging, category, importance, alerted)
                            )
                        }
    }

    // The slot's states (#161, ADR 0017).

    @Test
    fun `never granted offers the turn-on once, then rests without one`() {
        val first = resolveDigestSlot(false, educationShown = false, listenerConnected = false, emptyMap())
        assertIs<DigestSlot.Ungranted>(first)
        assertTrue(first.offersTurnOn)
        val declined = resolveDigestSlot(false, educationShown = true, listenerConnected = false, emptyMap())
        assertIs<DigestSlot.Ungranted>(declined)
        assertEquals(false, declined.offersTurnOn)
    }

    @Test
    fun `granted with nothing waiting is Empty, never a zero`() {
        assertIs<DigestSlot.Empty>(
            resolveDigestSlot(true, educationShown = true, listenerConnected = true, emptyMap())
        )
    }

    @Test
    fun `counts omit zero sections and keep display order`() {
        val slot = resolveDigestSlot(
            true, educationShown = true, listenerConnected = true,
            mapOf(
                DigestSection.Silent to 2,
                DigestSection.People to 3,
                DigestSection.Updates to 0,
            ),
        )
        assertIs<DigestSlot.Counts>(slot)
        assertEquals(mapOf(DigestSection.People to 3, DigestSection.Silent to 2), slot.counts)
        assertEquals("3 People · 2 Silent", digestLine(slot.counts))
    }

    @Test
    fun `a disconnected listener keeps the day's counts and says why`() {
        val slot = resolveDigestSlot(
            true, educationShown = true, listenerConnected = false,
            mapOf(DigestSection.Updates to 4),
        )
        assertIs<DigestSlot.Disconnected>(slot)
        assertEquals(mapOf(DigestSection.Updates to 4), slot.counts)
    }

    @Test
    fun `revoked before anything was counted is still Revoked, not never-granted`() {
        val slot = resolveDigestSlot(
            false, educationShown = true, listenerConnected = false,
            emptyMap(), grantSeen = true,
        )
        assertIs<DigestSlot.Revoked>(slot)
    }

    @Test
    fun `revoked mid-session keeps the day's counts and says why`() {
        val slot = resolveDigestSlot(
            false, educationShown = true, listenerConnected = false,
            mapOf(DigestSection.People to 1),
        )
        assertIs<DigestSlot.Revoked>(slot)
        assertEquals(mapOf(DigestSection.People to 1), slot.counts)
    }
}
