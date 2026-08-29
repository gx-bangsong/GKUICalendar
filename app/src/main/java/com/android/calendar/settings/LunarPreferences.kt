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

import android.content.Context
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import ws.xsoh.etar.R

/**
 * Settings screen for the contextual lunar calendar. Opened from
 * [GeneralPreferences] via `app:fragment`, exactly like
 * [ViewDetailsPreferences].
 */
class LunarPreferences : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = GeneralPreferences.SHARED_PREFS_NAME
        setPreferencesFromResource(R.xml.lunar_preferences, rootKey)
        // Show the selected mode as the summary, kept in sync automatically.
        findPreference<ListPreference>(KEY_MODE)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.title = getString(R.string.lunar_preferences_title)
    }

    companion object {
        // Keys must match res/xml/lunar_preferences.xml.
        const val KEY_MODE = "pref_lunar_mode"
        const val KEY_FESTIVALS = "pref_lunar_festivals"
        const val KEY_SHOW_JIEQI = "pref_lunar_show_jieqi"
        const val KEY_DETAIL_ALWAYS = "pref_lunar_detail_always"

        fun setDefaultValues(context: Context) {
            PreferenceManager.setDefaultValues(context, GeneralPreferences.SHARED_PREFS_NAME,
                Context.MODE_PRIVATE, R.xml.lunar_preferences, true)
        }
    }
}
