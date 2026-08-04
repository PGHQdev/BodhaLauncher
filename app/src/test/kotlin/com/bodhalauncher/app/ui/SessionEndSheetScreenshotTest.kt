package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The session-end moment's face under the screenshot gate (#26): fixed phrases,
 * no clock. Record: ./gradlew recordRoborazziDebug. Verify: ./gradlew verifyRoborazziDebug.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the launcher's runtimes (sessions, WorkManager) have no place under a screenshot.
@Config(sdk = [35], application = android.app.Application::class)
class SessionEndSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Sheet() {
        SessionEndSheetContent(
            phrase = "Your 10 minutes are complete.",
            onClose = {},
            onAddFive = {},
            onContinue = {},
        )
    }

    // The degraded path's honest copy — the moment could only be shown late (#75).
    @Composable
    private fun LateSheet() {
        SessionEndSheetContent(
            phrase = "Your 10 minutes ended 23 minutes ago.",
            onClose = {},
            onAddFive = {},
            onContinue = {},
        )
    }

    @Test
    fun session_end_light() {
        compose.setContent { BodhaTheme(darkTheme = false) { Sheet() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/session_end_light.png")
    }

    @Test
    fun session_end_dark() {
        compose.setContent { BodhaTheme(darkTheme = true) { Sheet() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/session_end_dark.png")
    }

    @Test
    fun session_end_late_light() {
        compose.setContent { BodhaTheme(darkTheme = false) { LateSheet() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/session_end_late_light.png")
    }

    @Test
    fun session_end_late_dark() {
        compose.setContent { BodhaTheme(darkTheme = true) { LateSheet() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/session_end_late_dark.png")
    }

    @Test
    fun session_end_light_large_type() {
        compose.setContent { LargeType { BodhaTheme(darkTheme = false) { Sheet() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/session_end_light_large_type.png")
    }

    @Test
    fun session_end_dark_large_type() {
        compose.setContent { LargeType { BodhaTheme(darkTheme = true) { Sheet() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/session_end_dark_large_type.png")
    }
}
