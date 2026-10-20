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
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.lunar.LunarFestivalSummary
import ws.xsoh.etar.R
import kotlin.math.max

/**
 * Settings screen for the contextual lunar calendar. Opened from
 * [GeneralPreferences] via `app:fragment`, exactly like [ViewDetailsPreferences].
 *
 * This is the Material 3 sample for the settings sub-pages. The rows use the layouts in
 * res/layout/preference_md3*.xml, which only res/xml/lunar_preferences.xml references.
 */
class LunarPreferences : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = GeneralPreferences.SHARED_PREFS_NAME
        setPreferencesFromResource(R.xml.lunar_preferences, rootKey)
        // Show the selected mode as the summary, kept in sync automatically.
        findPreference<ListPreference>(KEY_MODE)?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        // The festival summary follows the saved values in entry order.
        findPreference<MultiSelectListPreference>(KEY_FESTIVALS)?.summaryProvider =
            Preference.SummaryProvider<MultiSelectListPreference> { pref -> festivalSummary(pref) }
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val list = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        // On a wide window the content is centred in a column of at most CONTENT_MAX_WIDTH_DP.
        // A narrow window keeps the inherited padding. Only the horizontal padding changes, and
        // only when the value differs, so this does not loop.
        val basePadding = list.paddingStart
        val maxContentPx = (CONTENT_MAX_WIDTH_DP * resources.displayMetrics.density).toInt()
        list.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val side = max(basePadding, (right - left - maxContentPx) / 2)
            if (view.paddingStart != side || view.paddingEnd != side) {
                view.setPaddingRelative(side, view.paddingTop, side, view.paddingBottom)
            }
        }
        return list
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.title = getString(R.string.lunar_preferences_title)
    }

    private fun festivalSummary(pref: MultiSelectListPreference): CharSequence {
        val context = pref.context
        return LunarFestivalSummary.summarize(
            selected = pref.values,
            values = pref.entryValues.map { it.toString() },
            labels = pref.entries.map { it.toString() },
            allText = context.getString(R.string.lunar_festival_summary_all),
            noneText = context.getString(R.string.lunar_festival_summary_none),
            separator = context.getString(R.string.lunar_festival_summary_separator)
        )
    }

    companion object {
        // Keys must match res/xml/lunar_preferences.xml.
        const val KEY_MODE = "pref_lunar_mode"
        const val KEY_FESTIVALS = "pref_lunar_festivals"
        const val KEY_SHOW_JIEQI = "pref_lunar_show_jieqi"
        const val KEY_DETAIL_ALWAYS = "pref_lunar_detail_always"

        /** Maximum content width of the list on a wide window (MD3 medium window width). */
        private const val CONTENT_MAX_WIDTH_DP = 600

        fun setDefaultValues(context: Context) {
            PreferenceManager.setDefaultValues(context, GeneralPreferences.SHARED_PREFS_NAME,
                Context.MODE_PRIVATE, R.xml.lunar_preferences, true)
        }
    }
}
