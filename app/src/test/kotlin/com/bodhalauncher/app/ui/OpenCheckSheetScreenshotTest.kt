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
 * The Open Check sheet's face under the screenshot gate (#26): fixed content,
 * no icon (icons come from the device, not the design system), no clock.
 * Record: ./gradlew recordRoborazziDebug. Verify: ./gradlew verifyRoborazziDebug.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the launcher's runtimes (sessions, WorkManager) have no place under a screenshot.
@Config(sdk = [35], application = android.app.Application::class)
class OpenCheckSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Sheet() {
        OpenCheckSheetContent(appLabel = "Instagram", icon = null, onOpen = {}, onGoBack = {})
    }

    @Test
    fun open_check_light() {
        compose.setContent { BodhaTheme(darkTheme = false) { Sheet() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/open_check_light.png")
    }

    @Test
    fun open_check_dark() {
        compose.setContent { BodhaTheme(darkTheme = true) { Sheet() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/open_check_dark.png")
    }

    @Test
    fun open_check_light_large_type() {
        compose.setContent { LargeType { BodhaTheme(darkTheme = false) { Sheet() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/open_check_light_large_type.png")
    }

    @Test
    fun open_check_dark_large_type() {
        compose.setContent { LargeType { BodhaTheme(darkTheme = true) { Sheet() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/open_check_dark_large_type.png")
    }
}
