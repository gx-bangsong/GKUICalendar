/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.lunar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static source checks for the lunar settings list width and choice dialogs (shared helpers), run by the JVM test
 * task that CI executes.
 *
 * These are regression guards on the source text. They are NOT runtime proof. They do not show
 * that a row starts at the same x position on a device, or that a dialog renders, is cancelled,
 * or survives rotation. Those runtime checks have not been run in this environment.
 */
class LunarSettingsSourceTest {

    private val settingsDir = "src/main/java/com/android/calendar/settings"

    private fun source(name: String): String {
        val file = File("$settingsDir/$name")
        assertTrue("missing $settingsDir/$name (test working dir is the app module)", file.isFile)
        return file.readText()
    }

    @Test
    fun listWidthIsNotChangedAfterLayout() {
        // The earlier mechanism rewrote the list padding in an OnLayoutChangeListener, so rows
        // bound later used a different start edge. The width limit must stay in the measure pass.
        val prefs = source("LunarPreferences.kt")
        assertFalse(prefs.contains("addOnLayoutChangeListener"))
        assertFalse(prefs.contains("setPaddingRelative"))
        assertTrue(source("SettingsListLayout.kt").contains("override fun onMeasure"))
    }

    @Test
    fun choiceDialogsUseMaterialBuilderWithActivityContext() {
        val dialog = source("SettingsChoiceDialogFragment.kt")
        assertTrue(dialog.contains("MaterialAlertDialogBuilder(requireActivity())"))
        assertFalse(dialog.contains("AlertDialog.Builder("))
    }

    @Test
    fun lunarChoiceRowsUseTheSharedMaterialDialog() {
        // The lunar page's only ListPreference and MultiSelectListPreference rows go through the
        // shared helper. The helper routes by preference type, not by lunar key.
        val prefs = source("LunarPreferences.kt")
        assertTrue(prefs.contains("showSettingsChoiceDialog(preference)"))
        assertTrue(prefs.contains("super.onDisplayPreferenceDialog(preference)"))
        assertFalse(source("SettingsChoiceDialogFragment.kt").contains("pref_lunar"))
    }
}
