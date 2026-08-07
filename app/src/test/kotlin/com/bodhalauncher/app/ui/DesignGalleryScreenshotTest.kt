package com.bodhalauncher.app.ui

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
 * The design-system gate (#26, #27): both themes photographed from the gallery;
 * a drifted token fails the verify diff. Record: ./gradlew recordRoborazziDebug.
 * Verify: ./gradlew verifyRoborazziDebug.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the launcher's runtimes (sessions, WorkManager) have no place under a screenshot.
//
// Tall enough to hold the whole fixture at 2x text. The default 320x480 clipped
// it: the actionable components sit below the fold, and the large-type captures
// were cropped long before they were added — so "the layout holds at large type"
// was being asserted about the top third of the gallery.
//
// Raised from 3000 when ADR 0026's focused specimens were added: the large-type
// gallery already measured exactly 3000, which is the silent-clip signature.
// Raised again for ADR 0022's revealed affordances, which took the large-type
// fixture to 3975 — inside 4200, but a component away from clipping again.
// Raised for #135/#155's specimens (the promise step, the tinted list row).
@Config(sdk = [35], qualifiers = "w320dp-h6500dp", application = android.app.Application::class)
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
