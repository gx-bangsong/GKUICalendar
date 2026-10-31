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

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows a settings page's [ListPreference] or [MultiSelectListPreference] in an MD3 dialog.
 *
 * Returns true when the dialog was requested. Returns false for any other preference, which
 * then goes to the AndroidX dialog or to the page's own handling as before.
 *
 * The AndroidX dialog builds a plain AlertDialog.Builder, so the MD3 dialog shape does not apply
 * there. This fragment uses MaterialAlertDialogBuilder with the activity context, so the theme's
 * materialAlertDialogTheme applies.
 *
 * Behaviour matches the AndroidX dialogs it replaces:
 * - Single choice: tapping an entry closes the dialog, then calls the change listener and saves
 *   the value if the listener accepts it. Cancel, back and an outside tap change nothing.
 * - Multi choice: Ok saves the pending selection when it changed and the listener accepts it.
 *   Cancel, back and an outside tap leave the stored values unchanged.
 *
 * The choices come from the runtime preference (entries and entryValues), so values set in code
 * are shown. The pending selection is saved in the instance state, and the dialog is restored by
 * the child FragmentManager, so rotation keeps the selection without showing a second dialog.
 */
internal fun PreferenceFragmentCompat.showSettingsChoiceDialog(preference: Preference): Boolean {
    if (preference !is ListPreference && preference !is MultiSelectListPreference) return false
    // After rotation the child FragmentManager restores the dialog itself. Do not show a second.
    if (childFragmentManager.findFragmentByTag(SettingsChoiceDialogFragment.TAG) == null) {
        SettingsChoiceDialogFragment.newInstance(preference.key)
            .show(childFragmentManager, SettingsChoiceDialogFragment.TAG)
    }
    return true
}

internal class SettingsChoiceDialogFragment : DialogFragment() {

    private var pendingValues: MutableSet<String> = mutableSetOf()
    private var pendingChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            pendingValues = savedInstanceState.getStringArrayList(STATE_VALUES)
                ?.toMutableSet() ?: mutableSetOf()
            pendingChanged = savedInstanceState.getBoolean(STATE_CHANGED, false)
        } else {
            val pref = preference()
            pendingValues = if (pref is MultiSelectListPreference) {
                pref.values.toMutableSet()
            } else {
                mutableSetOf()
            }
            pendingChanged = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_VALUES, ArrayList(pendingValues))
        outState.putBoolean(STATE_CHANGED, pendingChanged)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val pref = preference()
            ?: throw IllegalStateException("No settings preference for key ${key()}")
        // dialogTitle is on DialogPreference. Fall back to the row title, as the AndroidX dialog.
        val title = (pref as? DialogPreference)?.dialogTitle ?: pref.title
        val builder = MaterialAlertDialogBuilder(requireActivity()).setTitle(title)
        return when (pref) {
            is ListPreference -> singleChoice(pref, builder)
            is MultiSelectListPreference -> multiChoice(pref, builder)
            else -> throw IllegalStateException("Unsupported preference for a choice dialog")
        }
    }

    private fun singleChoice(pref: ListPreference, builder: MaterialAlertDialogBuilder): Dialog {
        val entries = pref.entries
        val values = pref.entryValues.map { it.toString() }
        builder.setSingleChoiceItems(entries, pref.findIndexOfValue(pref.value)) { dialog, which ->
            // Close first, then save, as ListPreferenceDialogFragmentCompat does. A change that
            // recreates the activity (theme) then runs without a dialog still attached.
            dialog.dismiss()
            val value = values.getOrNull(which)
            if (value != null && pref.callChangeListener(value)) {
                pref.value = value
            }
        }
        // Choosing an entry closes the dialog, so there is no Ok button. Cancel stays.
        builder.setNegativeButton(
            pref.negativeButtonText ?: getString(android.R.string.cancel),
            null
        )
        return builder.create()
    }

    private fun multiChoice(
        pref: MultiSelectListPreference,
        builder: MaterialAlertDialogBuilder,
    ): Dialog {
        val entries = pref.entries
        val values = pref.entryValues.map { it.toString() }
        val checked = BooleanArray(values.size) { pendingValues.contains(values[it]) }
        builder.setMultiChoiceItems(entries, checked) { _, which, isChecked ->
            val value = values.getOrNull(which) ?: return@setMultiChoiceItems
            val changedNow = if (isChecked) pendingValues.add(value) else pendingValues.remove(value)
            pendingChanged = pendingChanged or changedNow
        }
        builder.setPositiveButton(
            pref.positiveButtonText ?: getString(android.R.string.ok)
        ) { _, _ -> commitMultiChoice(pref) }
        builder.setNegativeButton(
            pref.negativeButtonText ?: getString(android.R.string.cancel),
            null
        )
        return builder.create()
    }

    private fun commitMultiChoice(pref: MultiSelectListPreference) {
        // Save only when the selection changed, as MultiSelectListPreferenceDialogFragmentCompat.
        if (pendingChanged && pref.callChangeListener(HashSet(pendingValues))) {
            pref.setValues(HashSet(pendingValues))
        }
        pendingChanged = false
    }

    private fun key(): String = requireArguments().getString(ARG_KEY).orEmpty()

    private fun preference(): Preference? =
        (requireParentFragment() as PreferenceFragmentCompat).findPreference<Preference>(key())

    companion object {
        const val TAG = "settings_choice_dialog"
        private const val ARG_KEY = "settings_choice_key"
        private const val STATE_VALUES = "settings_choice_values"
        private const val STATE_CHANGED = "settings_choice_changed"

        fun newInstance(key: String): SettingsChoiceDialogFragment =
            SettingsChoiceDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
    }
}
