package com.bodhalauncher.app.opencheck

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.OpenCheckMode
import com.bodhalauncher.engine.OpenCheckRule
import com.bodhalauncher.engine.ScheduleWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Loading rules written by an older build. The store drops a line whose mode
 * the enum no longer carries, which is what retiring During Focus (#165) rests
 * on: the app opens as it did while that trigger was inert, and the surviving
 * rules — including the two-field lines of the mode-only era — are untouched.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class OpenCheckRuleStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun seedStoredRules() {
        context.getSharedPreferences("open_check_rules", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "rules",
                listOf(
                    "DuringFocus\t-\t-\tcom.retired.four",
                    "DuringFocus\tcom.retired.two",
                    "Always\tcom.kept.two",
                    "DailyThreshold\t30\t-\tcom.kept.threshold",
                    "Schedule\t-\t1320-360\tcom.kept.schedule",
                ).joinToString("\n"),
            )
            .commit()
    }

    @Test
    fun `a rule stored with a retired mode is dropped`() {
        val rules = OpenCheckRuleStore(context).rules.value
        assertNull(rules["com.retired.four"])
        assertNull(rules["com.retired.two"])
    }

    @Test
    fun `every other stored line parses unchanged`() {
        val rules = OpenCheckRuleStore(context).rules.value
        assertEquals(3, rules.size)
        assertEquals(OpenCheckMode.Always, rules.getValue("com.kept.two").mode)
        assertEquals(Duration.ofMinutes(30), rules.getValue("com.kept.threshold").dailyThreshold)
        assertEquals(ScheduleWindow(1320, 360), rules.getValue("com.kept.schedule").window)
    }

    /** Loading never rewrites the prefs, so the restart clause has to hold both before and after the next edit does. */
    @Test
    fun `the drop survives a restart`() {
        val surviving = setOf("com.kept.two", "com.kept.threshold", "com.kept.schedule")
        assertEquals(surviving, OpenCheckRuleStore(context).rules.value.keys)

        OpenCheckRuleStore(context).set("com.kept.two", OpenCheckRule(OpenCheckMode.Never))
        assertEquals(surviving, OpenCheckRuleStore(context).rules.value.keys)
    }
}
