/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Static checks on the lunar settings resources, run by the JVM test task that CI executes.
 *
 * What these tests cover:
 * - XML contract: keys, defaults, preference classes, layout references.
 * - Pairing contract: a switch row's Preference class binds one switch id, and the widget
 *   layout must declare that id.
 * - Classpath checks: the androidx and Material classes and ids named by those contracts exist
 *   in the dependency versions that CI resolves. These are metadata checks only.
 *
 * What these tests do NOT cover: view inflation, binding, checked state, taps, dialogs,
 * rotation. Those are runtime checks and have not been run in this environment.
 */
class LunarPreferencesXmlTest {

    private val appNs = "http://schemas.android.com/apk/res-auto"
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val storageKeys = setOf(
        "pref_lunar_mode",
        "pref_lunar_festivals",
        "pref_lunar_show_jieqi",
        "pref_lunar_detail_always"
    )
    private val switchKeys = setOf("pref_lunar_show_jieqi", "pref_lunar_detail_always")

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

    /**
     * The switch id each Preference class binds, from the androidx.preference 1.2.1 source.
     * SwitchPreferenceCompat looks up R.id.switchWidget. SwitchPreference looks up
     * android.R.id.switch_widget. Any other class is not a supported switch row here.
     */
    private fun boundSwitchIdFor(preferenceTag: String): String = when (preferenceTag) {
        "SwitchPreferenceCompat" -> "@+id/switchWidget"
        "SwitchPreference" -> "@android:id/switch_widget"
        else -> throw AssertionError("unsupported switch preference class: $preferenceTag")
    }

    @Test
    fun keysAreUnchanged() {
        // Storage keys are read by other code. Changing one loses user data.
        assertEquals(storageKeys, prefs().mapNotNull { it.app("key") }.toSet())
    }

    @Test
    fun defaultsAreUnchanged() {
        assertEquals("@string/lunar_mode_default", byKey("pref_lunar_mode").app("defaultValue"))
        assertEquals("@array/lunar_festival_defaults", byKey("pref_lunar_festivals").app("defaultValue"))
        assertEquals("false", byKey("pref_lunar_show_jieqi").app("defaultValue"))
        assertEquals("false", byKey("pref_lunar_detail_always").app("defaultValue"))
    }

    @Test
    fun preferenceClassesAreTheExpectedOnes() {
        assertEquals("ListPreference", byKey("pref_lunar_mode").tagName)
        assertEquals("MultiSelectListPreference", byKey("pref_lunar_festivals").tagName)
        // Boolean rows use the compat class. It stores the same Boolean under the same key.
        for (key in switchKeys) {
            assertEquals("SwitchPreferenceCompat", byKey(key).tagName)
        }
    }

    @Test
    fun rowsAndHeadingsUseTheMd3Layouts() {
        val categories = prefs().filter { it.tagName == "PreferenceCategory" }
        assertEquals(2, categories.size)
        categories.forEach { category ->
            assertEquals("@layout/preference_md3_category", category.app("layout"))
        }
        for (key in storageKeys) {
            assertEquals("@layout/preference_md3", byKey(key).app("layout"))
        }
    }

    @Test
    fun switchRowsPairTheirPreferenceClassWithTheIdItBinds() {
        // The row class decides which id is bound. The widget layout must declare that id.
        // A mismatch leaves the switch unbound: it shows the wrong state and never syncs.
        val widgetRef = "@layout/preference_md3_switch"
        val switchId = parseSwitchLayoutId()
        for (key in switchKeys) {
            val row = byKey(key)
            assertEquals("widget layout for $key", widgetRef, row.app("widgetLayout"))
            assertEquals(
                "switch id for ${row.tagName} ($key)",
                boundSwitchIdFor(row.tagName),
                switchId
            )
        }
    }

    @Test
    fun headingsHaveNoKey() {
        // A category has no key and is not a stored preference, so tapping it cannot change a value.
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
    fun switchLayoutIsAMaterialSwitchThatIsNotFocusableOrClickable() {
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

    // ---- Classpath checks: metadata from the resolved dependencies, not view runtime. ----

    @Test
    fun classpathPreferenceClassIsTwoStateAndHasTheBoundIdName() {
        val compat = Class.forName("androidx.preference.SwitchPreferenceCompat", false, javaClass.classLoader)
        val twoState = Class.forName("androidx.preference.TwoStatePreference", false, javaClass.classLoader)
        assertTrue("SwitchPreferenceCompat must extend TwoStatePreference", twoState.isAssignableFrom(compat))
        // The library R class must declare the id name the widget layout uses.
        val idClass = Class.forName("androidx.preference.R\$id", false, javaClass.classLoader)
        assertTrue("androidx.preference.R.id.switchWidget is missing", hasField(idClass, "switchWidget"))
    }

    @Test
    fun classpathMaterialSwitchIsASwitchCompat() {
        // SwitchPreferenceCompat only attaches its listener when the widget is a SwitchCompat.
        val switchCompat = Class.forName("androidx.appcompat.widget.SwitchCompat", false, javaClass.classLoader)
        val materialSwitch = Class.forName(
            "com.google.android.material.materialswitch.MaterialSwitch", false, javaClass.classLoader
        )
        assertTrue("MaterialSwitch must extend SwitchCompat", switchCompat.isAssignableFrom(materialSwitch))
    }

    private fun parseSwitchLayoutId(): String {
        val root = parse("src/main/res/layout/preference_md3_switch.xml").documentElement
        return root.getAttributeNS(androidNs, "id")
    }

    private fun hasField(type: Class<*>, name: String): Boolean =
        type.fields.any { it.name == name }
}
