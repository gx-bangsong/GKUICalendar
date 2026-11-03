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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static guards for the calendar settings page (CalendarPreferences), run by the JVM test task
 * that CI executes (the `com.android.calendar.settings.*` filter in app/build.gradle.kts).
 *
 * They check the source text and the layout files only. They do not show the switch drawn in
 * Material 3 style, alignment, rotation, or TalkBack. Those need a device.
 */
class CalendarPreferencesSourceTest {

    private val settingsDir = "src/main/java/com/android/calendar/settings"
    private val resDir = "src/main/res"

    private fun source(name: String): String {
        val file = File("$settingsDir/$name")
        assertTrue("missing $settingsDir/$name (test working dir is the app module)", file.isFile)
        return file.readText()
    }

    private fun layout(name: String): String {
        val file = File("$resDir/layout/$name")
        assertTrue("missing $resDir/layout/$name", file.isFile)
        return file.readText()
    }

    @Test
    fun visibleSwitchUsesCompatPreferenceAndMaterialWidget() {
        val src = source("CalendarPreferences.kt")
        assertTrue(src.contains("SwitchPreferenceCompat(context)"))
        // The framework SwitchPreference binds android:id/switch_widget, not the MD3 switch.
        assertFalse(Regex("(?<!Compat)SwitchPreference\\(").containsMatchIn(src))
        assertTrue(src.contains("pref.widgetLayoutResource = R.layout.preference_md3_switch"))
    }

    @Test
    fun rowsAndCategoriesUseMaterial3Layouts() {
        val src = source("CalendarPreferences.kt")
        assertTrue(src.contains("pref.layoutResource = R.layout.preference_md3\n"))
        assertTrue(src.contains("pref.layoutResource = R.layout.preference_md3_category"))
        assertTrue(src.contains("applyMd3Layouts(screen)"))
    }

    @Test
    fun md3LayoutFilesExistAndKeepBindingIds() {
        assertTrue(layout("preference_md3.xml").contains("@android:id/title"))
        assertTrue(layout("preference_md3.xml").contains("@android:id/widget_frame"))
        assertTrue(layout("preference_md3_switch.xml").contains("@+id/switchWidget"))
        assertTrue(layout("preference_md3_category.xml").contains("@android:id/title"))
    }

    @Test
    fun wideScreensKeepTheSharedListWidth() {
        val src = source("CalendarPreferences.kt")
        assertTrue(src.contains("override fun onCreateRecyclerView"))
        assertTrue(src.contains("wrapSettingsListWidth(list, parent, resources.displayMetrics.density)"))
    }

    @Test
    fun displayNameRenameUsesMd3TextDialog() {
        val src = source("CalendarPreferences.kt")
        assertTrue(src.contains("showSettingsTextDialog(preference)"))
        assertTrue(source("SettingsTextDialogFragment.kt").contains("MaterialAlertDialogBuilder"))
        assertTrue(layout("settings_text_dialog.xml").contains("Widget.Material3.TextInputLayout.OutlinedBox"))
    }

    @Test
    fun manageNotificationsRowHandlesClickInsteadOfOnlyAnIntent() {
        val src = source("CalendarPreferences.kt")
        assertTrue(Regex("setOnPreferenceClickListener\\s*\\{\\s*openNotificationSettings\\(\\)").containsMatchIn(src))
        assertTrue(src.contains("createPerCalendarChannels(context, nm)"))
        assertTrue(src.contains("Settings.ACTION_APP_NOTIFICATION_SETTINGS"))
    }
}
