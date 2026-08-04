package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The design-system gate (#26, #27): both themes photographed from the gallery;
 * a drifted token fails the verify diff. Record: ./gradlew recordRoborazziDebug.
 * Verify: ./gradlew verifyRoborazziDebug.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the launcher's runtimes (sessions, WorkManager) have no place under a screenshot.
@Config(sdk = [35], application = android.app.Application::class)
class DesignGalleryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun gallery_light() {
        compose.setContent { BodhaTheme(darkTheme = false) { DesignGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/gallery_light.png")
    }

    @Test
    fun gallery_dark() {
        compose.setContent { BodhaTheme(darkTheme = true) { DesignGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/gallery_dark.png")
    }

    // Dynamic-type support is part of the identity (#26): the layout must hold at 2x text.
    @Test
    fun gallery_light_large_type() {
        compose.setContent { LargeType { BodhaTheme(darkTheme = false) { DesignGallery() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/gallery_light_large_type.png")
    }

    @Test
    fun gallery_dark_large_type() {
        compose.setContent { LargeType { BodhaTheme(darkTheme = true) { DesignGallery() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/gallery_dark_large_type.png")
    }
}

@Composable
private fun LargeType(content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f), content = content)
}
