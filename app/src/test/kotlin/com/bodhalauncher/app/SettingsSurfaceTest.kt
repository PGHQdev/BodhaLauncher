package com.bodhalauncher.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.engine.SETTINGS_ROWS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Settings' shape (#140): one list defines the rows and the catalogue, so this
 * asserts the surface renders exactly the catalogue — a row dropped by a
 * condition, or drawn under a different string than Search would match, fails
 * here. A row added with no rendering does not reach this test at all: the
 * surface's `when` on [com.bodhalauncher.engine.SettingsRowId] is exhaustive, so
 * it fails to compile first.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp", application = android.app.Application::class)
class SettingsSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun actionableNames(): List<String> {
        val found = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            if (SemanticsActions.OnClick in node.config) found += node
            node.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return found.mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }
    }

    private fun setSurface(homeRoleHeld: Boolean = true, onRequestHomeRole: () -> Unit = {}) =
        compose.setContent {
            BodhaTheme {
                SettingsSurface(homeRoleHeld = homeRoleHeld, onRequestHomeRole = onRequestHomeRole)
            }
        }

    @Test
    fun `the rendered rows and the catalogue entries are the same set`() {
        setSurface()

        assertEquals(SETTINGS_ROWS.map { it.label }.toSet(), actionableNames().toSet())
    }

    @Test
    fun `the home-role row states that Bodha holds the role`() {
        setSurface(homeRoleHeld = true)

        compose.onNodeWithText("Bodha is your home app").assertExists()
    }

    @Test
    fun `and states the declined case without re-prompting`() {
        var requests = 0
        setSurface(homeRoleHeld = false, onRequestHomeRole = { requests++ })

        compose.onNodeWithText("Bodha is an app you open").assertExists()
        assertEquals("arriving on Settings asks for nothing", 0, requests)
    }

    @Test
    fun `tapping the row re-opens the system role request`() {
        var requests = 0
        setSurface(homeRoleHeld = false, onRequestHomeRole = { requests++ })

        compose.onNodeWithText("Home app").performClick()
        assertEquals(1, requests)
    }
}
