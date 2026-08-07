package com.bodhalauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.AWARENESS_TURN_ON_USAGE
import com.bodhalauncher.engine.AwarenessUsage
import com.bodhalauncher.engine.AwarenessView
import com.bodhalauncher.engine.AwarenessWeek
import com.bodhalauncher.engine.IntentSignal
import com.bodhalauncher.engine.SessionRecord
import com.bodhalauncher.engine.resolveAwarenessWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Awareness's Week view as it renders (#176): seven days in calendar order, each
 * with its count and its split, and two period rates sitting adjacent as bare
 * numbers — no arrow, no sign, no colour and no day ranked above another.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp", application = android.app.Application::class)
class AwarenessWeekScreenTest {

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 7, 14, 0)
        val TODAY: LocalDate = LocalDate.of(2026, 8, 7)
        val DATES = listOf(
            "Saturday, 1 August", "Sunday, 2 August", "Monday, 3 August",
            "Tuesday, 4 August", "Wednesday, 5 August", "Thursday, 6 August",
            "Friday, 7 August",
        )

        /** 3.1h/day this week, 3.4h/day the one before — the ticket's own figures. */
        const val THIS_PERIOD = 78_120_000L
        const val LAST_PERIOD = 85_680_000L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun nodes(): List<SemanticsNode> {
        val found = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            found += node
            node.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return found
    }

    private fun drawnText(): List<String> = nodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
        .map { it.text }

    private fun actionable(): List<SemanticsNode> =
        nodes().filter { SemanticsActions.OnClick in it.config }

    private fun record(day: LocalDate, hour: Int, id: Long) = SessionRecord(
        id = id,
        start = day.atTime(hour, 0),
        end = day.atTime(hour, 0).plusMinutes(20),
        day = day,
    )

    /** Two sessions on the Monday, one of them stated; one on the live day. */
    private val records = listOf(
        record(LocalDate.of(2026, 8, 3), hour = 9, id = 1),
        record(LocalDate.of(2026, 8, 3), hour = 11, id = 2),
        record(TODAY, hour = 9, id = 3),
    )

    private val signals = listOf(IntentSignal(at = LocalDate.of(2026, 8, 3).atTime(9, 5)))

    private fun week(
        records: List<SessionRecord> = this.records,
        usage: AwarenessUsage = AwarenessUsage.Live,
        millis: Long? = THIS_PERIOD,
        previousMillis: Long? = LAST_PERIOD,
    ) = resolveAwarenessWeek(records, signals, millis, previousMillis, usage, NOW)

    private val opened = mutableListOf<LocalDate>()
    private val picked = mutableListOf<AwarenessView>()
    private var turnedOn = 0

    private fun setScreen(
        week: AwarenessWeek? = week(),
        usage: AwarenessUsage = AwarenessUsage.Live,
    ) = compose.setContent {
        BodhaTheme {
            AwarenessWeekScreen(
                week = week,
                usage = usage,
                onPickView = { picked += it },
                onOpenDay = { opened += it },
                onTurnOnUsage = { turnedOn++ },
            )
        }
    }

    @Test
    fun `seven days render in calendar order, each with its count and its split`() {
        setScreen()

        val drawn = drawnText()
        assertEquals(DATES, drawn.filter { it in DATES })
        assertTrue("2 sessions · 1 intentional · 1 unclassified" in drawn)
        assertTrue("1 session · 1 unclassified" in drawn)
    }

    /** A day ordered by a figure would be a ranking (ADR 0013); the date decides. */
    @Test
    fun `days are ordered by date and never by any figure`() {
        setScreen(week(records = records.shuffled()))

        assertEquals(DATES, drawnText().filter { it in DATES })
    }

    /** A quiet day is a sentence under the same row, never a 0 in a count field. */
    @Test
    fun `a quiet day names itself empty and draws no zero`() {
        setScreen()

        val drawn = drawnText()
        assertEquals(5, drawn.count { it == "No sessions" })
        for (line in drawn) {
            assertTrue("\"$line\" is a bare zero", line.trim() != "0")
            assertTrue("\"$line\" counts nothing", !line.startsWith("0 "))
            assertTrue("\"$line\" counts nothing", !line.contains(" 0 "))
        }
    }

    /** Bare numbers, adjacent — the whole of what ADR 0013 permits (#11). */
    @Test
    fun `the two periods sit adjacent as bare rates with no sign and no delta`() {
        setScreen()

        assertTrue("This week 3.1h/day · last week 3.4h/day" in drawnText())
        assertEquals(emptyList<String>(), drawnText().filter { it.contains("→") || it.contains("↑") })
    }

    @Test
    fun `no drawn string carries a direction word, a sign or a ranking`() {
        setScreen()

        val forbidden = listOf("+", "more", "less", "up", "down", "better", "worse", "most", "least")
        for (line in drawnText()) {
            for (word in forbidden) {
                assertTrue(
                    "\"$line\" carries \"$word\" (ADR 0013)",
                    !line.lowercase().split(" ", "·").contains(word),
                )
            }
        }
    }

    /**
     * The three figures the ticket names and nothing else: a count, a split, and
     * one rate per period. No ratio, no median, no burst — the metrics
     * `computeMetrics` owns are read off the event log while these counts come
     * off the session records, and one row showing two answers to one question
     * is what ADR 0013's single vocabulary exists to prevent.
     */
    @Test
    fun `nothing beyond the session count, the split and the rate renders`() {
        setScreen()

        val drawn = drawnText()
        val allowed = Regex(
            """Awareness|Today|Week|This week .*|No sessions|›|""" +
                """\d+ sessions?( · \d+ intentional)?( · \d+ unclassified)?|""" +
                DATES.joinToString("|")
        )
        assertEquals(emptyList<String>(), drawn.filterNot { allowed.matches(it) })
    }

    /** The absence is stated once, above the rows, and never on a row. */
    @Test
    fun `without usage access the week names what is missing once`() {
        val usage = AwarenessUsage.Ungranted(offersTurnOn = true)
        setScreen(week(usage = usage), usage)

        val drawn = drawnText()
        assertEquals(1, drawn.count { it == "Foreground time needs usage access" })
        assertTrue(AWARENESS_TURN_ON_USAGE in drawn)
        // No rate line at all, rather than a rate with nothing to divide.
        assertEquals(emptyList<String>(), drawn.filter { it.startsWith("This week") })
        // The records are untouched by the absence.
        assertTrue("2 sessions · 1 intentional · 1 unclassified" in drawn)
    }

    /**
     * Access held and the read still came back with nothing: the figure is
     * unavailable and says so, rather than rendering as a line that quietly did
     * not appear — and never as a 0 (#176, ADR 0013).
     */
    @Test
    fun `a granted read that came back with nothing names the absence, not a zero`() {
        setScreen(week(millis = null, previousMillis = null))

        val drawn = drawnText()
        assertTrue("Foreground time could not be read" in drawn)
        assertEquals(emptyList<String>(), drawn.filter { it.startsWith("This week") })
        assertTrue(drawn.none { it.trim() == "0" })
        // Nothing to act on: the grant is held, so there is no way in to offer.
        assertEquals(9, actionable().size)
    }

    @Test
    fun `a declined education sheet drops the turn-on and leaves the plain note`() {
        val usage = AwarenessUsage.Ungranted(offersTurnOn = false)
        setScreen(week(usage = usage), usage)

        assertTrue("Foreground time needs usage access" in drawnText())
        assertTrue(AWARENESS_TURN_ON_USAGE !in drawnText())
        // Only the seven day rows and the two switch pills stay actionable.
        assertEquals(9, actionable().size)
    }

    @Test
    fun `the turn-on row enters the education flow as a feature touch`() {
        val usage = AwarenessUsage.Ungranted(offersTurnOn = true)
        setScreen(week(usage = usage), usage)

        val row = actionable().single { it.spokenName() == "Foreground time needs usage access" }
        row.config[SemanticsActions.OnClick].action?.invoke()
        assertEquals(1, turnedOn)
        // Rule 3: it opens a sheet, so it draws no chevron — the seven that
        // navigate do, and nothing else does.
        assertEquals(7, drawnText().count { it == "›" })
    }

    /** ADR 0025 rule 2: the tint says which view holds, and so does the semantics. */
    @Test
    fun `exactly one switch pill reports itself selected`() {
        setScreen()

        assertEquals(
            listOf("Today" to false, "Week" to true),
            nodes().filter { SemanticsProperties.Selected in it.config }
                .map { it.spokenName() to it.config[SemanticsProperties.Selected] },
        )
    }

    /** ADR 0020: one node per day, named by what it says, at the floor on both axes. */
    @Test
    fun `each day row is one actionable node named by its date and its figures`() {
        setScreen()

        val floor = with(compose.density) { TOUCH_TARGET_MIN.roundToPx() }
        val rows = actionable().filter { it.spokenName() in DATES }
        assertEquals(7, rows.size)
        for (row in rows) {
            val lines = row.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertTrue("${row.spokenName()} says nothing about itself", lines.size >= 2)
            assertTrue(
                "${row.spokenName()} = ${row.size.width}x${row.size.height}px",
                row.size.width >= floor && row.size.height >= floor,
            )
        }
    }

    @Test
    fun `a day row opens the day it names`() {
        setScreen()

        actionable().single { it.spokenName() == "Monday, 3 August" }
            .config[SemanticsActions.OnClick].action?.invoke()
        assertEquals(listOf(LocalDate.of(2026, 8, 3)), opened)
    }

    /**
     * Arrival lands on the first row, which is what gives Escape a chain to
     * travel up (ADR 0022). Arrived at by key rather than composed already-open,
     * because that is the only sequence in which arrival focus can happen: a row
     * is `clickable`, so it is focusable in non-touch mode only.
     */
    @Test
    fun `the week arrives with its first day row focused, and Escape leaves for root`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                var open by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    if (open) {
                        AwarenessWeekScreen(
                            week = week(),
                            usage = AwarenessUsage.Live,
                            onPickView = {},
                            onOpenDay = {},
                            onTurnOnUsage = {},
                        )
                    } else {
                        ListRow("Open the week", onClick = { open = true })
                    }
                }
            }
        }

        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals("Saturday, 1 August", compose.focusedNameIn(ACTIVITY_ROOT))

        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }

    /**
     * A read that has not landed renders no row, so arrival goes to the current
     * switch pill instead — never nowhere, because a surface that focuses
     * nothing has no back key at all (ADR 0022's build amendment).
     */
    @Test
    fun `a view with no rows arrives on the current pill`() {
        var backs = 0
        compose.setContent {
            BodhaTheme {
                BackHandler { backs++ }
                var open by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().escapeIsBack()) {
                    if (open) {
                        AwarenessWeekScreen(
                            week = null,
                            usage = AwarenessUsage.Live,
                            onPickView = {},
                            onOpenDay = {},
                            onTurnOnUsage = {},
                        )
                    } else {
                        ListRow("Open the week", onClick = { open = true })
                    }
                }
            }
        }

        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals("Week", compose.focusedNameIn(ACTIVITY_ROOT))

        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }
}
