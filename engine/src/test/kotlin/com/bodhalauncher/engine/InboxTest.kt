package com.bodhalauncher.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxTest {

    private fun row(
        key: String,
        section: DigestSection = DigestSection.Updates,
        appPackage: String = "com.example",
        postedAt: Long = 0L,
    ) = InboxRow(key = key, appPackage = appPackage, section = section, postedAtMillis = postedAt)

    // -- state resolution: three named absences, never a blank list (#162) --

    @Test
    fun `no grant is access-off, whatever the rows claim`() {
        val state = resolveInbox(
            granted = false, listenerConnected = true, rows = listOf(row("a")),
        )
        assertEquals(InboxState.AccessOff, state)
    }

    @Test
    fun `granted but unbound is disconnected, not empty`() {
        val state = resolveInbox(granted = true, listenerConnected = false, rows = emptyList())
        assertEquals(InboxState.Disconnected, state)
    }

    @Test
    fun `connected with nothing live is nothing waiting`() {
        val state = resolveInbox(granted = true, listenerConnected = true, rows = emptyList())
        assertEquals(InboxState.Empty, state)
    }

    // -- grouping (#162) --

    @Test
    fun `rows group under their sections in display order`() {
        val state = resolveInbox(
            granted = true, listenerConnected = true,
            rows = listOf(
                row("u", section = DigestSection.Updates),
                row("p", section = DigestSection.People),
                row("s", section = DigestSection.Silent),
            ),
        ) as InboxState.Sections
        assertEquals(
            listOf(DigestSection.People, DigestSection.Updates, DigestSection.Silent),
            state.sections.map { it.section },
        )
    }

    @Test
    fun `a section with nothing in it is absent rather than empty`() {
        val state = resolveInbox(
            granted = true, listenerConnected = true,
            rows = listOf(row("p", section = DigestSection.People)),
        ) as InboxState.Sections
        assertEquals(listOf(DigestSection.People), state.sections.map { it.section })
    }

    @Test
    fun `within a section the newest row comes first`() {
        val state = resolveInbox(
            granted = true, listenerConnected = true,
            rows = listOf(row("old", postedAt = 1), row("new", postedAt = 2)),
        ) as InboxState.Sections
        assertEquals(listOf("new", "old"), state.sections.single().rows.map { it.key })
    }

    // -- mute (#164): a muted source neither rows nor counts, others untouched --

    @Test
    fun `a muted source's rows are gone and the rest stand`() {
        val state = resolveInbox(
            granted = true, listenerConnected = true,
            rows = listOf(
                row("kept", appPackage = "com.kept"),
                row("muted", appPackage = "com.muted"),
            ),
            muted = setOf("com.muted"),
        ) as InboxState.Sections
        assertEquals(listOf("kept"), state.sections.single().rows.map { it.key })
    }

    @Test
    fun `everything muted reads as nothing waiting, not a blank list`() {
        val state = resolveInbox(
            granted = true, listenerConnected = true,
            rows = listOf(row("muted", appPackage = "com.muted")),
            muted = setOf("com.muted"),
        )
        assertEquals(InboxState.Empty, state)
    }

    // -- snooze durations (#163): three fixed choices, no custom picker --

    @Test
    fun `the snooze sheet offers fifteen minutes, one hour and four hours`() {
        assertEquals(
            listOf(15L * 60_000, 60L * 60_000, 240L * 60_000),
            SNOOZE_CHOICES.map { it.durationMillis },
        )
        assertTrue(SNOOZE_CHOICES.all { it.label.isNotBlank() })
    }
}
