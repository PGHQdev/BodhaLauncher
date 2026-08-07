package com.bodhalauncher.engine

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DaySlotTest {

    private val noon = LocalDateTime.of(2026, 8, 5, 12, 0)

    private fun instance(
        id: Long = 1,
        title: String = "Event",
        allDay: Boolean = false,
        begin: LocalDateTime = noon.plusHours(1),
        end: LocalDateTime = noon.plusHours(2),
        visible: Boolean = true,
        declined: Boolean = false,
    ) = ProviderInstance(id, title, allDay, begin, end, visible, declined)

    private fun resolve(
        instances: List<ProviderInstance>,
        now: LocalDateTime = noon,
        granted: Boolean = true,
        educationShown: Boolean = false,
        hasCalendars: Boolean = true,
    ) = resolveDaySlot(granted, educationShown, hasCalendars, instances, now)

    @Test
    fun `orders earliest first with all-day rows above timed ones`() {
        val slot = resolve(
            listOf(
                instance(id = 1, title = "Late", begin = noon.plusHours(6), end = noon.plusHours(7)),
                instance(id = 2, title = "Soon", begin = noon.plusHours(1), end = noon.plusHours(2)),
                instance(
                    id = 3, title = "All day", allDay = true,
                    begin = noon.minusHours(12), end = noon.plusHours(12),
                ),
            )
        )
        assertIs<DaySlot.Events>(slot)
        assertEquals(listOf("All day", "Soon", "Late"), slot.events.map { it.title })
    }

    @Test
    fun `declined instances and hidden calendars are absent`() {
        val slot = resolve(
            listOf(
                instance(id = 1, title = "Kept"),
                instance(id = 2, title = "Declined", declined = true),
                instance(id = 3, title = "Hidden", visible = false),
            )
        )
        assertIs<DaySlot.Events>(slot)
        assertEquals(listOf("Kept"), slot.events.map { it.title })
    }

    @Test
    fun `an ended event is gone, one in progress is present`() {
        val slot = resolve(
            listOf(
                instance(id = 1, title = "Ended", begin = noon.minusHours(3), end = noon.minusHours(1)),
                instance(id = 2, title = "Running", begin = noon.minusHours(1), end = noon.plusHours(1)),
            )
        )
        assertIs<DaySlot.Events>(slot)
        assertEquals(listOf("Running"), slot.events.map { it.title })
    }

    @Test
    fun `an event spanning midnight survives into the new day key`() {
        // 1:30am: still the previous day key; the party that started at 11pm shows.
        val small = LocalDateTime.of(2026, 8, 6, 1, 30)
        val slot = resolve(
            listOf(
                instance(
                    id = 1, title = "Party",
                    begin = LocalDateTime.of(2026, 8, 5, 23, 0),
                    end = LocalDateTime.of(2026, 8, 6, 2, 30),
                )
            ),
            now = small,
        )
        assertIs<DaySlot.Events>(slot)
        assertEquals(listOf("Party"), slot.events.map { it.title })
    }

    @Test
    fun `an all-day event holds until the day key rolls, not until midnight`() {
        val allDay = instance(
            id = 1, title = "Holiday", allDay = true,
            begin = LocalDateTime.of(2026, 8, 5, 0, 0),
            end = LocalDateTime.of(2026, 8, 6, 0, 0),
        )
        // 1:30am Aug 6: still Aug 5's day key, so the day's all-day row remains.
        assertIs<DaySlot.Events>(resolve(listOf(allDay), now = LocalDateTime.of(2026, 8, 6, 1, 30)))
        // 4:30am Aug 6: the key has rolled; the row is gone.
        assertIs<DaySlot.Empty>(resolve(listOf(allDay), now = LocalDateTime.of(2026, 8, 6, 4, 30)))
    }

    @Test
    fun `an event beginning after the day key ends is not today's`() {
        // At 11pm, a 9am-tomorrow meeting is beyond the 4am boundary.
        val evening = LocalDateTime.of(2026, 8, 5, 23, 0)
        val slot = resolve(
            listOf(
                instance(
                    id = 1, title = "Tomorrow",
                    begin = LocalDateTime.of(2026, 8, 6, 9, 0),
                    end = LocalDateTime.of(2026, 8, 6, 10, 0),
                )
            ),
            now = evening,
        )
        assertIs<DaySlot.Empty>(slot)
    }

    @Test
    fun `ungranted before education offers the turn-on`() {
        val slot = resolve(emptyList(), granted = false, educationShown = false)
        assertIs<DaySlot.Ungranted>(slot)
        assertTrue(slot.offersTurnOn)
    }

    @Test
    fun `a declined education leaves no inert control`() {
        val slot = resolve(emptyList(), granted = false, educationShown = true)
        assertIs<DaySlot.Ungranted>(slot)
        assertEquals(false, slot.offersTurnOn)
    }

    @Test
    fun `no calendars configured names its own cause, never granted-and-empty`() {
        assertIs<DaySlot.NoCalendars>(resolve(emptyList(), hasCalendars = false))
    }

    @Test
    fun `granted with calendars but nothing left is Empty`() {
        assertIs<DaySlot.Empty>(resolve(emptyList()))
    }

    // #160 — the Tomorrow peek.

    private fun resolveWithTomorrow(
        today: List<ProviderInstance>,
        tomorrow: List<ProviderInstance>,
        now: LocalDateTime = noon,
    ) = resolveDaySlot(
        granted = true, educationShown = false, hasCalendars = true,
        instances = today, now = now, tomorrowInstances = tomorrow,
    )

    @Test
    fun `with the day spent, tomorrow's first event peeks`() {
        val slot = resolveWithTomorrow(
            today = emptyList(),
            tomorrow = listOf(
                instance(
                    id = 1, title = "Nine o'clock",
                    begin = noon.plusDays(1).minusHours(3), end = noon.plusDays(1).minusHours(2),
                ),
                instance(
                    id = 2, title = "Tomorrow's all-day", allDay = true,
                    begin = noon.plusDays(1).minusHours(12), end = noon.plusDays(1).plusHours(12),
                ),
            ),
        )
        assertIs<DaySlot.Empty>(slot)
        // The day slot's own ordering: the all-day event wins over the 9am one.
        assertEquals("Tomorrow's all-day", slot.tomorrowFirst?.title)
    }

    @Test
    fun `with any event left today, no peek appears`() {
        val slot = resolveWithTomorrow(
            today = listOf(instance(id = 1, title = "Still to come")),
            tomorrow = listOf(instance(id = 2, title = "Tomorrow")),
        )
        assertIs<DaySlot.Events>(slot)
    }

    @Test
    fun `with tomorrow empty too, the slot rests on its sentence`() {
        val slot = resolveWithTomorrow(today = emptyList(), tomorrow = emptyList())
        assertIs<DaySlot.Empty>(slot)
        assertEquals(null, slot.tomorrowFirst)
    }

    @Test
    fun `a declined or hidden tomorrow event never peeks`() {
        val slot = resolveWithTomorrow(
            today = emptyList(),
            tomorrow = listOf(
                instance(id = 1, title = "Declined", declined = true),
                instance(id = 2, title = "Hidden", visible = false),
            ),
        )
        assertIs<DaySlot.Empty>(slot)
        assertEquals(null, slot.tomorrowFirst)
    }

    @Test
    fun `at 1-30am the 9am meeting reads as tomorrow`() {
        // Tomorrow is the day after the current day key: at 1:30am Aug 6 the key
        // is still Aug 5, so Aug 6's 9am meeting is tomorrow's — how people
        // speak at 1:30am. The caller windows by day key; the reducer peeks it.
        val small = LocalDateTime.of(2026, 8, 6, 1, 30)
        val slot = resolveWithTomorrow(
            today = emptyList(),
            tomorrow = listOf(
                instance(
                    id = 1, title = "Morning meeting",
                    begin = LocalDateTime.of(2026, 8, 6, 9, 0),
                    end = LocalDateTime.of(2026, 8, 6, 10, 0),
                )
            ),
            now = small,
        )
        assertIs<DaySlot.Empty>(slot)
        assertEquals("Morning meeting", slot.tomorrowFirst?.title)
    }

    @Test
    fun `after 4am that same meeting is today's, not a peek`() {
        val morning = LocalDateTime.of(2026, 8, 6, 4, 30)
        val meeting = instance(
            id = 1, title = "Morning meeting",
            begin = LocalDateTime.of(2026, 8, 6, 9, 0),
            end = LocalDateTime.of(2026, 8, 6, 10, 0),
        )
        val slot = resolveWithTomorrow(today = listOf(meeting), tomorrow = emptyList(), now = morning)
        assertIs<DaySlot.Events>(slot)
        assertEquals(listOf("Morning meeting"), slot.events.map { it.title })
    }
}
