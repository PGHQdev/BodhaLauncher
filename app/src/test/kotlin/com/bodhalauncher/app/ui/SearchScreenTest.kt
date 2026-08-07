package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.AppResult
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.REASON_PINNED
import com.bodhalauncher.engine.SearchInputs
import com.bodhalauncher.engine.SearchResult
import com.bodhalauncher.engine.SearchSection
import com.bodhalauncher.engine.SearchShortcut
import com.bodhalauncher.engine.ShortcutResult
import com.bodhalauncher.engine.resolveSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Search as a docked and as a touch user meets it (#180).
 *
 * Default graphics mode for the reason [KeyboardOperabilityTest] takes it:
 * Compose focus traversal does not run under `@GraphicsMode(NATIVE)`, and this
 * suite needs no pixels.
 *
 * The IME showing on arrival is the one clause here that is not asserted —
 * Robolectric has no input method to observe. What is asserted is the half that
 * is observable: the field holds focus the moment the surface composes, with no
 * key pressed first.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp", application = android.app.Application::class)
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val installed =
        listOf("Instagram", "Telegram", "Camera").map { HomeAction(id = it.lowercase(), label = it) }

    private val shortcuts = listOf(
        SearchShortcut(id = "new-chat", appId = "telegram", label = "New chat"),
        SearchShortcut(id = "selfie", appId = "camera", label = "New selfie"),
        SearchShortcut(id = "camera-roll", appId = "instagram", label = "Camera roll"),
    )

    /** The surface's own state loop, so what is driven is a real query round trip. */
    @Composable
    private fun Search(pinned: Set<String> = emptySet(), onOpen: (SearchResult) -> Unit = {}) {
        var query by remember { mutableStateOf("") }
        SearchScreen(
            state = resolveSearch(
                SearchInputs(apps = installed, shortcuts = shortcuts, query = query, pinned = pinned)
            ),
            query = query,
            onQueryChange = { query = it },
            iconFor = { null },
            iconKey = Unit,
            onOpen = onOpen,
        )
    }

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

    private fun focusedName(): String? = nodes()
        .filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
        .mapNotNull {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }
        .firstOrNull()

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun type(text: String) =
        compose.onNodeWithContentDescription(SEARCH_FIELD_LABEL).performTextInput(text)

    @Test
    fun `search opens on the field with nothing listed`() {
        compose.setContent { BodhaTheme { Search() } }

        assertEquals(SEARCH_FIELD_LABEL, focusedName())
        installed.forEach { assertFalse(it.label, it.label in drawnText()) }
        assertFalse("nothing has been typed, so nothing has failed to match", SEARCH_NOTHING_FOUND in drawnText())
    }

    @Test
    fun `typing lists the apps whose words the query prefixes`() {
        compose.setContent { BodhaTheme { Search() } }

        type("insta")

        assertTrue("Instagram" in drawnText())
        assertFalse("gram is mid-word in Telegram", "Telegram" in drawnText())
    }

    @Test
    fun `clearing the query back to whitespace lists nothing again`() {
        compose.setContent { BodhaTheme { Search() } }

        type("insta")
        compose.onNodeWithContentDescription(SEARCH_FIELD_LABEL).performTextClearance()
        type("   ")

        assertFalse("Instagram" in drawnText())
        assertFalse(SEARCH_NOTHING_FOUND in drawnText())
    }

    @Test
    fun `a query matching nothing shows one empty state`() {
        compose.setContent { BodhaTheme { Search() } }

        type("gram")

        assertEquals(1, drawnText().count { it == SEARCH_NOTHING_FOUND })
    }

    @Test
    fun `tapping a result opens it`() {
        var opened: SearchResult? = null
        compose.setContent { BodhaTheme { Search(onOpen = { opened = it }) } }

        type("insta")
        compose.onNodeWithText("Instagram").performClick()

        assertEquals("Instagram", opened?.label)
    }

    @Test
    fun `down from the field enters the first result and enter opens it`() {
        var opened: SearchResult? = null
        compose.setContent { BodhaTheme { Search(onOpen = { opened = it }) } }

        type("insta")
        press(Key.DirectionDown)
        assertEquals("Instagram", focusedName())

        press(Key.Enter)
        assertEquals("Instagram", opened?.label)
    }

    @Test
    fun `sections draw under their overlines in the fixed order`() {
        compose.setContent { BodhaTheme { Search() } }

        type("cam")

        val drawn = drawnText()
        val appsHeading = drawn.indexOf(SearchSection.Apps.heading)
        val shortcutsHeading = drawn.indexOf(SearchSection.Shortcuts.heading)
        assertTrue("apps section drawn", appsHeading >= 0)
        assertTrue("shortcuts section drawn", shortcutsHeading >= 0)
        assertTrue("apps before shortcuts", appsHeading < shortcutsHeading)
        assertTrue("Camera" in drawn)
    }

    @Test
    fun `a matching shortcut opens as a shortcut`() {
        var opened: SearchResult? = null
        compose.setContent { BodhaTheme { Search(onOpen = { opened = it }) } }

        type("chat")
        compose.onNodeWithText("New chat").performClick()

        assertEquals("new-chat", (opened as? ShortcutResult)?.shortcut?.id)
    }

    @Test
    fun `a shortcut whose app also matched stays out of the list`() {
        compose.setContent { BodhaTheme { Search() } }

        type("tele")

        assertTrue("Telegram" in drawnText())
        assertFalse("New chat" in drawnText())
        assertFalse(SearchSection.Shortcuts.heading in drawnText())
    }

    @Test
    fun `a lifted result carries its reason line, and only then`() {
        compose.setContent { BodhaTheme { Search(pinned = setOf("instagram")) } }

        type("insta")
        assertTrue(REASON_PINNED in drawnText())

        compose.onNodeWithContentDescription(SEARCH_FIELD_LABEL).performTextClearance()
        type("tele")
        assertFalse(REASON_PINNED in drawnText())
    }

    @Test
    fun `down from the field enters the first row of the first section`() {
        var opened: SearchResult? = null
        compose.setContent { BodhaTheme { Search(onOpen = { opened = it }) } }

        type("cam")
        press(Key.DirectionDown)
        assertEquals("Camera", focusedName())

        press(Key.Enter)
        assertEquals("camera", (opened as? AppResult)?.app?.id)
    }
}
