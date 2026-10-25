/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.calendar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Static guards for the General and Display details pages, run by the JVM test task that CI
 * executes (see the filter in app/build.gradle.kts).
 *
 * These check the XML structure and the source text. They are regression guards only. They do
 * not show alignment, dialog rendering, rotation, TalkBack or font scale. Those need a device,
 * and none was run for this change.
 */
class SettingsPagesSourceTest {

    private val settingsDir = "src/main/java/com/android/calendar/settings"
    private val resDir = "src/main/res"
    private val appNs = "http://schemas.android.com/apk/res-auto"

    private fun source(name: String): String {
        val file = File("$settingsDir/$name")
        assertTrue("missing $settingsDir/$name (test working dir is the app module)", file.isFile)
        return file.readText()
    }

    private fun xmlRoot(path: String): Element {
        val file = File("$resDir/$path")
        assertTrue("missing $resDir/$path", file.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.elements(): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            (nodes.item(i) as? Element)?.let { out.add(it) }
        }
        return out
    }

    private fun Element.walk(): List<Element> =
        elements().flatMap { listOf(it) + it.walk() }

    private fun Element.app(name: String): String? = getAttributeNS(appNs, name).ifEmpty { null }

    /** Every row (not a category or screen) of a settings XML. */
    private fun rows(root: Element): List<Element> =
        root.walk().filter { it.tagName in ROW_TAGS }

    // ---- Shared helpers -------------------------------------------------------------------

    @Test
    fun sharedListWidthStaysInMeasurePass() {
        val layout = source("SettingsListLayout.kt")
        assertTrue(layout.contains("internal const val SETTINGS_LIST_MAX_WIDTH_DP = 600"))
        assertTrue(layout.contains("override fun onMeasure"))
        assertFalse(layout.contains("addOnLayoutChangeListener"))
        assertFalse(layout.contains("setPaddingRelative"))
    }

    @Test
    fun sharedChoiceDialogIsGenericAndUsesMaterialBuilder() {
        val dialog = source("SettingsChoiceDialogFragment.kt")
        assertTrue(dialog.contains("MaterialAlertDialogBuilder(requireActivity())"))
        assertFalse(dialog.contains("AlertDialog.Builder("))
        // Routes by type, not by key: no page key may leak into the shared helper.
        assertTrue(dialog.contains("preference !is ListPreference && preference !is MultiSelectListPreference"))
        assertFalse(dialog.contains("pref_lunar"))
        assertFalse(dialog.contains("pref_theme"))
        assertFalse(dialog.contains("preferences_"))
    }

    @Test
    fun generalAndDisplayPagesRouteOnlyChoiceRowsThroughSharedDialog() {
        for (name in listOf("GeneralPreferences.kt", "ViewDetailsPreferences.kt")) {
            val page = source(name)
            assertTrue("$name wraps the list width", page.contains("wrapSettingsListWidth("))
            assertTrue("$name uses the shared dialog", page.contains("showSettingsChoiceDialog(preference)"))
            assertTrue("$name keeps the framework path", page.contains("super.onDisplayPreferenceDialog(preference)"))
        }
    }

    @Test
    fun ciRunsTheSettingsTests() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("filter.includeTestsMatching(\"com.android.calendar.settings.*\")"))
    }

    // ---- Preference types and keys ---------------------------------------------------------

    @Test
    fun switchesAreSwitchPreferenceCompatAndBindToTheMd3Switch() {
        for (path in PAGE_XML) {
            val root = xmlRoot(path)
            assertFalse("$path has no plain SwitchPreference", root.walk().any { it.tagName == "SwitchPreference" })
            assertFalse("$path has no CheckBoxPreference", root.walk().any { it.tagName == "CheckBoxPreference" })
            for (sw in root.walk().filter { it.tagName == "SwitchPreferenceCompat" }) {
                assertEquals("$path ${sw.app("key")} widget", "@layout/preference_md3_switch", sw.app("widgetLayout"))
            }
        }
        assertTrue(File("$resDir/layout/preference_md3_switch.xml").readText().contains("@+id/switchWidget"))
    }

    @Test
    fun everyRowAndCategoryUsesTheMd3Layouts() {
        for (path in PAGE_XML) {
            val root = xmlRoot(path)
            for (row in rows(root)) {
                val key = row.app("key")
                assertEquals("$path $key layout", "@layout/preference_md3", row.app("layout"))
                assertEquals("$path $key iconSpaceReserved", "false", row.app("iconSpaceReserved"))
            }
            for (cat in root.walk().filter { it.tagName == "PreferenceCategory" }) {
                assertEquals("$path category layout", "@layout/preference_md3_category", cat.app("layout"))
            }
        }
    }

    @Test
    fun noFrameworkRowLayoutOrSummaryFormatRemains() {
        for (path in PAGE_XML) {
            val text = File("$resDir/$path").readText()
            assertFalse("$path still uses the framework child layout", text.contains("preferenceLayoutChild"))
            assertFalse("$path still uses a %s summary", text.contains("%s"))
        }
    }

    @Test
    fun generalPageKeysAndDefaultsAreUnchanged() {
        val pre26 = xmlRoot("xml/general_preferences.xml")
        val keys = rows(pre26).associate { it.app("key") to it.tagName }
        // Migrated rows keep their key; the tag changes to SwitchPreferenceCompat.
        assertEquals("SwitchPreferenceCompat", keys["preferences_alerts"])
        assertEquals("SwitchPreferenceCompat", keys["preferences_home_tz_enabled"])
        assertEquals("ListPreference", keys["pref_theme"])
        assertEquals("Preference", keys["preferences_home_tz"])
        assertEquals("false", rows(pre26).first { it.app("key") == "preferences_home_tz_enabled" }.app("defaultValue"))
        assertEquals("true", rows(pre26).first { it.app("key") == "preferences_alerts" }.app("defaultValue"))
        assertEquals("classic", rows(pre26).first { it.app("key") == "preferences_month_layout" }.app("defaultValue"))

        val v26 = xmlRoot("xml-v26/general_preferences.xml")
        assertEquals("SwitchPreferenceCompat", rows(v26).first { it.app("key") == "preferences_home_tz_enabled" }.tagName)
        assertTrue(rows(v26).none { it.app("key") == "preferences_alerts" })
    }

    @Test
    fun displayDetailsKeysAndOrientationGroupsAreUnchanged() {
        val root = xmlRoot("xml/view_details_preferences.xml")
        val groups = root.walk().filter { it.tagName == "PreferenceCategory" }
        // Month portrait and landscape keep their two groups. The day and week background switch
        // has its own third group, so the month groups are never mixed with it.
        assertEquals(3, groups.size)
        val portrait = groups[0].walk().filter { it.tagName in ROW_TAGS }.map { it.app("key") }
        val landscape = groups[1].walk().filter { it.tagName in ROW_TAGS }.map { it.app("key") }
        assertEquals(
            listOf("pref_day_week_background_mask"),
            groups[2].walk().filter { it.tagName in ROW_TAGS }.map { it.app("key") },
        )
        assertEquals(
            "false",
            root.walk().first { it.app("key") == "pref_day_week_background_mask" }.app("defaultValue"),
        )
        assertEquals(
            listOf(
                "pref_number_of_lines_vertical",
                "pref_display_time_vertical",
                "pref_display_location_vertical",
            ),
            portrait,
        )
        assertEquals(
            listOf(
                "pref_number_of_lines_horizontal",
                "pref_display_time_horizontal",
                "pref_display_location_horizontal",
            ),
            landscape,
        )
        assertEquals("7", root.walk().first { it.app("key") == "pref_number_of_lines_vertical" }.app("defaultValue"))
        assertEquals("7", root.walk().first { it.app("key") == "pref_number_of_lines_horizontal" }.app("defaultValue"))
    }

    // ---- Display details code --------------------------------------------------------------

    @Test
    fun displayDetailsTrimsEntryValuesWithEntries() {
        val code = source("ViewDetailsPreferences.kt")
        assertTrue(code.contains("displayTime.entries = Arrays.copyOf(entries, entries.size - 1)"))
        assertTrue(code.contains("displayTime.entryValues = Arrays.copyOf(values, values.size - 1)"))
    }

    @Test
    fun displayDetailsSummariesUseSummaryProvider() {
        val code = source("ViewDetailsPreferences.kt")
        assertTrue(code.contains("ListPreference.SimpleSummaryProvider.getInstance()"))
        assertTrue(code.contains("LIST_KEYS"))
    }

    @Test
    fun generalPageKeepsSharedPreferencesFileAndScrollRestore() {
        val general = source("GeneralPreferences.kt")
        assertTrue(general.contains("const val SHARED_PREFS_NAME = \"com.android.calendar_preferences\""))
        assertTrue(general.contains("override fun onDestroyView()"))
        assertTrue(general.contains("override fun onBindPreferences()"))
        // The page must not reintroduce a plain SwitchPreference or CheckBoxPreference type.
        assertFalse(Regex("\\b(SwitchPreference|CheckBoxPreference)\\b").containsMatchIn(general))
        val details = source("ViewDetailsPreferences.kt")
        assertFalse(Regex("\\b(SwitchPreference|CheckBoxPreference)\\b").containsMatchIn(details))
    }

    private companion object {
        val ROW_TAGS = setOf("ListPreference", "MultiSelectListPreference", "Preference", "SwitchPreferenceCompat")
        val PAGE_XML = listOf(
            "xml/general_preferences.xml",
            "xml-v26/general_preferences.xml",
            "xml/view_details_preferences.xml",
            "xml/lunar_preferences.xml",
        )
    }
}
