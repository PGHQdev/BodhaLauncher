package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeReducerTest {

    private fun action(id: String) = HomeAction(id = id, label = id)

    @Test
    fun `empty inputs still yield a valid Home`() {
        val home = resolveHome(HomeInputs())

        assertTrue(home.actions.isEmpty())
        assertNull(home.dailyIntention)
        assertNull(home.contextLabel)
        assertNull(home.inboxDigest)
        assertNull(home.sessionIntent)
        assertEquals(false, home.focusActive)
    }

    @Test
    fun `pins outrank suggestions`() {
        val home = resolveHome(
            HomeInputs(
                pinned = listOf(action("camera"), action("notes")),
                suggested = listOf(action("messages")),
            )
        )

        assertEquals(listOf("camera", "notes", "messages"), home.actions.map { it.id })
    }

    @Test
    fun `action list caps at four with pins first`() {
        val home = resolveHome(
            HomeInputs(
                pinned = listOf(action("p1"), action("p2"), action("p3")),
                suggested = listOf(action("s1"), action("s2")),
            )
        )

        assertEquals(listOf("p1", "p2", "p3", "s1"), home.actions.map { it.id })
    }

    @Test
    fun `pins alone can fill all four slots`() {
        val home = resolveHome(
            HomeInputs(
                pinned = listOf(action("p1"), action("p2"), action("p3"), action("p4"), action("p5")),
            )
        )

        assertEquals(listOf("p1", "p2", "p3", "p4"), home.actions.map { it.id })
    }

    @Test
    fun `hidden suggestions are excluded`() {
        val home = resolveHome(
            HomeInputs(
                suggested = listOf(action("feed"), action("notes")),
                hidden = setOf("feed"),
            )
        )

        assertEquals(listOf("notes"), home.actions.map { it.id })
    }

    @Test
    fun `a suggestion already pinned is not repeated`() {
        val home = resolveHome(
            HomeInputs(
                pinned = listOf(action("camera")),
                suggested = listOf(action("camera"), action("notes")),
            )
        )

        assertEquals(listOf("camera", "notes"), home.actions.map { it.id })
    }

    @Test
    fun `hiding never removes a pin`() {
        val home = resolveHome(
            HomeInputs(
                pinned = listOf(action("camera")),
                hidden = setOf("camera"),
            )
        )

        assertEquals(listOf("camera"), home.actions.map { it.id })
    }

    @Test
    fun `present inputs pass through to the resolved state`() {
        val home = resolveHome(
            HomeInputs(
                dailyIntention = "Finish Bodha prototype",
                contextLabel = "Work",
                inboxDigest = "3 people reached out",
                focusActive = true,
                sessionIntent = IntentCategory.Communicate,
            )
        )

        assertEquals("Finish Bodha prototype", home.dailyIntention)
        assertEquals("Work", home.contextLabel)
        assertEquals("3 people reached out", home.inboxDigest)
        assertEquals(true, home.focusActive)
        assertEquals(IntentCategory.Communicate, home.sessionIntent)
    }
}
