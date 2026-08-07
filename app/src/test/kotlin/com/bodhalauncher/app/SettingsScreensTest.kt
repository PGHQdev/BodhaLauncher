package com.bodhalauncher.app

import com.bodhalauncher.engine.ActionResult
import com.bodhalauncher.engine.SearchInputs
import com.bodhalauncher.engine.SearchSection
import com.bodhalauncher.engine.resolveSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue's matching, independent of any device (#188): the full list runs
 * through the real reducer, so what is proven is the labels people will type
 * against the rule they will be matched by. Device resolution is
 * [resolvedSettingsScreens]'s and is deliberately not exercised here.
 */
class SettingsScreensTest {

    private fun search(query: String) = resolveSearch(
        SearchInputs(actions = SETTINGS_SCREENS.map { it.searchAction() }, query = query)
    )

    private fun actionLabels(query: String): List<String> =
        search(query).sections.firstOrNull { it.section == SearchSection.Actions }
            ?.rows?.map { it.result.label }.orEmpty()

    @Test
    fun `the household names each find their screen`() {
        assertEquals(listOf("Wi-Fi"), actionLabels("wifi"))
        assertEquals(listOf("Bluetooth"), actionLabels("bluetooth"))
        assertTrue("Battery" in actionLabels("battery"))
    }

    @Test
    fun `matching is word-boundary prefix, as everywhere`() {
        assertTrue("Airplane mode" in actionLabels("mode"))
        assertTrue(actionLabels("tooth").isEmpty())
    }

    @Test
    fun `every entry is findable by its own label`() {
        SETTINGS_SCREENS.forEach { screen ->
            assertTrue(screen.label, actionLabels(screen.label).contains(screen.label))
        }
    }

    @Test
    fun `catalogue rows come out as action results with distinct ids`() {
        val ids = SETTINGS_SCREENS.map { it.searchAction().id }
        assertEquals(ids.size, ids.toSet().size)
        search("wifi").sections.flatMap { it.rows }.forEach {
            assertTrue(it.result is ActionResult)
        }
    }
}
