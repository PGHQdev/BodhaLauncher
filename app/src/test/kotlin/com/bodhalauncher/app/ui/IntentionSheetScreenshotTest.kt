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
 * Today's intention editor under the screenshot gate (#26): the empty state
 * with yesterday's suggestion, and the set state with Clear. Fixed content.
 * Record: ./gradlew recordRoborazziDebug. Verify: ./gradlew verifyRoborazziDebug.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h3000dp", application = android.app.Application::class)
class IntentionSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun EmptyWithSuggestion() {
        IntentionSheetContent(
            current = null,
            suggestion = "finish the reading, then rest",
            onSave = {},
            onClear = {},
        )
    }

    @Composable
    private fun SetState() {
        IntentionSheetContent(
            current = "write the second chapter",
            suggestion = null,
            onSave = {},
            onClear = {},
        )
    }

    @Test
    fun intention_sheet_empty_light() {
        compose.setContent { BodhaTheme(darkTheme = false) { EmptyWithSuggestion() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/intention_sheet_empty_light.png")
    }

    @Test
    fun intention_sheet_empty_dark() {
        compose.setContent { BodhaTheme(darkTheme = true) { EmptyWithSuggestion() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/intention_sheet_empty_dark.png")
    }

    @Test
    fun intention_sheet_set_light() {
        compose.setContent { BodhaTheme(darkTheme = false) { SetState() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/intention_sheet_set_light.png")
    }

    @Test
    fun intention_sheet_set_light_large_type() {
        compose.setContent { LargeType { BodhaTheme(darkTheme = false) { SetState() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/intention_sheet_set_light_large_type.png")
    }
}
