package com.bodhalauncher.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.ContextMode
import com.bodhalauncher.engine.ScheduleWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A mode's editor (#156): its window shown where it is edited, and reordering as
 * two named rows — no drag handle, because a drag has no keyboard route
 * (ADR 0022) and would owe a `// reachable:` marker under ADR 0024's guard.
 *
 * The editor is driven as [ModeEditorContent] rather than through its dialog:
 * see [ACTIVITY_ROOT]'s note on why a `BasicTextField` inside a `Dialog` cannot
 * be driven here at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp", application = android.app.Application::class)
class ModeDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private val modes = listOf(
        ContextMode("Work", ScheduleWindow(9 * 60, 18 * 60)),
        ContextMode("Evening", ScheduleWindow(18 * 60, 22 * 60)),
        ContextMode("Weekend"),
    )

    private val moves = mutableListOf<Pair<String, Int>>()
    private val windows = mutableListOf<ScheduleWindow?>()

    private fun setEditor(name: String) {
        val mode = modes.single { it.name == name }
        compose.setContent {
            BodhaTheme {
                ModeEditorContent(
                    mode = mode,
                    canMoveUp = modes.first() != mode,
                    canMoveDown = modes.last() != mode,
                    onRename = { null },
                    onSetWindow = { windows += it },
                    onMove = { moves += mode.name to it },
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
    }

    private fun setManage() {
        compose.setContent {
            BodhaTheme {
                ModeManageDialog(
                    modes = modes,
                    onCreate = { null },
                    onRename = { _, _ -> null },
                    onDelete = {},
                    onSetWindow = { _, _ -> },
                    onMove = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `the manage list shows each mode's window, and names its absence`() {
        setManage()

        // The second line of a merged row, which is why this reads drawn text
        // rather than the names a reader would announce the rows by.
        val drawn = compose.drawnTextIn(DIALOG_ROOT)
        assertTrue("9:00 to 18:00" in drawn)
        assertTrue("18:00 to 22:00" in drawn)
        assertTrue("No time window" in drawn)
    }

    @Test
    fun `move up is reached by Tab and performed by Enter`() {
        setEditor("Evening")

        compose.tabTo(ACTIVITY_ROOT, "Move Evening up")
        compose.press(ACTIVITY_ROOT, Key.Enter)

        assertEquals(listOf("Evening" to -1), moves)
    }

    @Test
    fun `move down is reached by Tab and performed by Enter`() {
        setEditor("Evening")

        compose.tabTo(ACTIVITY_ROOT, "Move Evening down")
        compose.press(ACTIVITY_ROOT, Key.Enter)

        assertEquals(listOf("Evening" to 1), moves)
    }

    /** The row is absent at the end it cannot move towards, rather than present and inert. */
    @Test
    fun `the first mode is offered no move up`() {
        setEditor("Work")

        val names = compose.namesIn(ACTIVITY_ROOT)
        assertFalse("Move Work up" in names)
        assertTrue("Move Work down" in names)
    }

    @Test
    fun `the last mode is offered no move down`() {
        setEditor("Weekend")

        val names = compose.namesIn(ACTIVITY_ROOT)
        assertTrue("Move Weekend up" in names)
        assertFalse("Move Weekend down" in names)
    }

    @Test
    fun `a mode with no window is offered no way to remove one`() {
        setEditor("Weekend")

        assertFalse("Remove time window" in compose.namesIn(ACTIVITY_ROOT))
    }

    @Test
    fun `removing a window is reached by Tab and performed by Enter`() {
        setEditor("Evening")

        compose.tabTo(ACTIVITY_ROOT, "Remove time window")
        compose.press(ACTIVITY_ROOT, Key.Enter)

        assertEquals(listOf<ScheduleWindow?>(null), windows)
    }

    /** The editor is where a mode's window is set, using the shared editor (#74, #156). */
    @Test
    fun `the time window row opens the shared window editor in the mode's own words`() {
        setEditor("Evening")

        compose.tabTo(ACTIVITY_ROOT, "Time window")
        compose.press(ACTIVITY_ROOT, Key.Enter)

        val drawn = compose.drawnTextIn(ACTIVITY_ROOT) + compose.namesIn(ACTIVITY_ROOT)
        assertTrue("Switch to Evening between these times" in drawn)
        assertTrue("Start time" in drawn)
        assertTrue("End time" in drawn)
    }
}
