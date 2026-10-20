/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the contract that the lunar settings XML must keep. These are static checks on the
 * resource files, so they run in the JVM test task that CI executes. They do not replace a
 * device check.
 */
class LunarPreferencesXmlTest {

    private val appNs = "http://schemas.android.com/apk/res-auto"
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val file = File(path)
        assertTrue("missing $path (test working dir is the app module)", file.isFile)
        return factory.newDocumentBuilder().parse(file)
    }

    private fun prefs(): List<Element> {
        val root = parse("src/main/res/xml/lunar_preferences.xml").documentElement
        val nodes = root.getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.app(name: String): String? =
        if (hasAttributeNS(appNs, name)) getAttributeNS(appNs, name) else null

    private fun byKey(key: String): Element =
        prefs().firstOrNull { it.app("key") == key }
            ?: throw AssertionError("no preference with key $key")

    @Test
    fun keysAreUnchanged() {
        // Storage keys are read by other code. Changing one loses user data.
        val keys = prefs().mapNotNull { it.app("key") }.toSet()
        assertEquals(
            setOf("pref_lunar_mode", "pref_lunar_festivals", "pref_lunar_show_jieqi", "pref_lunar_detail_always"),
            keys
        )
    }

    @Test
    fun defaultsAreUnchanged() {
        assertEquals("@string/lunar_mode_default", byKey("pref_lunar_mode").app("defaultValue"))
        assertEquals("@array/lunar_festival_defaults", byKey("pref_lunar_festivals").app("defaultValue"))
        assertEquals("false", byKey("pref_lunar_show_jieqi").app("defaultValue"))
        assertEquals("false", byKey("pref_lunar_detail_always").app("defaultValue"))
    }

    @Test
    fun preferenceTypesAreUnchanged() {
        assertEquals("ListPreference", byKey("pref_lunar_mode").tagName)
        assertEquals("MultiSelectListPreference", byKey("pref_lunar_festivals").tagName)
        assertEquals("SwitchPreference", byKey("pref_lunar_show_jieqi").tagName)
        assertEquals("SwitchPreference", byKey("pref_lunar_detail_always").tagName)
    }

    @Test
    fun rowsAndHeadingsUseTheMd3Layouts() {
        val categories = prefs().filter { it.tagName == "PreferenceCategory" }
        assertEquals(2, categories.size)
        categories.forEach { category ->
            assertEquals("@layout/preference_md3_category", category.app("layout"))
        }
        // Each row inside a group uses the MD3 row layout, and every switch the switch layout.
        for (key in listOf("pref_lunar_mode", "pref_lunar_festivals", "pref_lunar_show_jieqi", "pref_lunar_detail_always")) {
            assertEquals("@layout/preference_md3", byKey(key).app("layout"))
        }
        for (key in listOf("pref_lunar_show_jieqi", "pref_lunar_detail_always")) {
            assertEquals("@layout/preference_md3_switch", byKey(key).app("widgetLayout"))
        }
    }

    @Test
    fun headingsAreNotClickable() {
        // A category has no key and is not a stored preference, so tapping it can't change a value.
        prefs().filter { it.tagName == "PreferenceCategory" }.forEach {
            assertNull("category must not have a key", it.app("key"))
        }
    }

    @Test
    fun referencedLayoutsExist() {
        val refs = prefs().flatMap { listOfNotNull(it.app("layout"), it.app("widgetLayout")) }.toSet()
        assertTrue(refs.isNotEmpty())
        refs.forEach { ref ->
            assertTrue(ref.startsWith("@layout/"))
            val name = ref.removePrefix("@layout/")
            assertTrue("missing layout $name", File("src/main/res/layout/$name.xml").isFile)
        }
    }

    @Test
    fun rowColorReferencesExist() {
        // Text colours of the row must be state lists, so disabled rows are dimmed.
        val root = parse("src/main/res/layout/preference_md3.xml").documentElement
        val nodes = root.getElementsByTagName("*")
        val colors = (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttributeNS(androidNs, "textColor") }
            .filter { it.startsWith("@color/") }
        assertEquals(2, colors.size)
        colors.forEach { ref ->
            val name = ref.removePrefix("@color/")
            assertTrue("missing color $name", File("src/main/res/color/$name.xml").isFile)
        }
    }

    @Test
    fun switchLayoutKeepsTheSingleToggleContract() {
        // The row toggles once when tapped. The switch must not take focus or clicks,
        // otherwise the row and the switch can both change the value.
        val root = parse("src/main/res/layout/preference_md3_switch.xml").documentElement
        assertEquals("com.google.android.material.materialswitch.MaterialSwitch", root.tagName)
        assertEquals("@+id/switchWidget", root.getAttributeNS(androidNs, "id"))
        assertEquals("false", root.getAttributeNS(androidNs, "focusable"))
        assertEquals("false", root.getAttributeNS(androidNs, "clickable"))
    }

    @Test
    fun rowLayoutExposesTheIdsPreferenceBindsTo() {
        // Preference finds these views by id. A missing id leaves the row blank or unbound.
        val root = parse("src/main/res/layout/preference_md3.xml").documentElement
        val nodes = root.getElementsByTagName("*")
        val found = (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttributeNS(androidNs, "id") }
            .filter { it.isNotEmpty() }
            .toSet()
        assertEquals(
            setOf(
                "@+id/icon_frame",
                "@android:id/icon",
                "@android:id/title",
                "@android:id/summary",
                "@android:id/widget_frame"
            ),
            found
        )
    }
}
