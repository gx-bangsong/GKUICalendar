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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.lunar.LunarFestivalSummary
import ws.xsoh.etar.R
import kotlin.math.roundToInt

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
        // On a device where the framework already placed the list in its own layout (Android
        // Auto), leave that layout alone.
        if (list.parent != null) return list
        // On a wide window the list is centred in a column of at most CONTENT_MAX_WIDTH_DP.
        // The width limit is applied by the wrapper's measure pass, so no padding is changed
        // after layout and every row uses the same start edge from its first layout.
        val maxWidthPx = (CONTENT_MAX_WIDTH_DP * resources.displayMetrics.density).roundToInt()
        val column = MaxWidthFrameLayout(parent.context, maxWidthPx)
        column.addView(
            list,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        parent.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL
            )
        )
        return list
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key !in CHOICE_KEYS) {
            super.onDisplayPreferenceDialog(preference)
            return
        }
        // After rotation the child FragmentManager restores the dialog itself. Do not show a
        // second one.
        if (childFragmentManager.findFragmentByTag(LunarChoiceDialogFragment.TAG) != null) return
        LunarChoiceDialogFragment.newInstance(preference.key)
            .show(childFragmentManager, LunarChoiceDialogFragment.TAG)
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

        /** Rows that open the MD3 choice dialog. */
        private val CHOICE_KEYS = setOf(KEY_MODE, KEY_FESTIVALS)

        /**
         * Maximum width of the settings list on a wide window. This is a project choice, not an
         * MD3 requirement; the MD3 medium window width is 600dp.
         */
        private const val CONTENT_MAX_WIDTH_DP = 600

        fun setDefaultValues(context: Context) {
            PreferenceManager.setDefaultValues(context, GeneralPreferences.SHARED_PREFS_NAME,
                Context.MODE_PRIVATE, R.xml.lunar_preferences, true)
        }
    }
}
