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
 * Dialog for the two lunar choice rows: the mode (single choice) and the festivals (multi
 * choice). It is shown by [LunarPreferences] and replaces the AndroidX dialog for these keys.
 *
 * The AndroidX dialog builds a plain AlertDialog.Builder, which does not apply the MD3 dialog
 * shape. This fragment uses MaterialAlertDialogBuilder with the activity context, so the theme's
 * materialAlertDialogTheme (28dp corners) applies.
 *
 * Behaviour matches the AndroidX dialogs:
 * - Single choice: tapping an entry saves it at once, if the change listener accepts it, and
 *   closes the dialog. Cancel, back and an outside tap change nothing.
 * - Multi choice: Ok saves the pending selection when it changed and the change listener accepts
 *   it. Cancel, back and an outside tap leave the stored values unchanged.
 *
 * The pending selection is kept in the saved state, so rotation keeps it. The dialog is restored
 * by the child FragmentManager, so rotation does not show a second one.
 */
internal class LunarChoiceDialogFragment : DialogFragment() {

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
            ?: throw IllegalStateException("No lunar preference for key ${key()}")
        // dialogTitle lives on DialogPreference; fall back to the row title like the AndroidX dialog.
        val title = (pref as? DialogPreference)?.dialogTitle ?: pref.title
        val builder = MaterialAlertDialogBuilder(requireActivity()).setTitle(title)
        return when (pref) {
            is ListPreference -> singleChoice(pref, builder)
            is MultiSelectListPreference -> multiChoice(pref, builder)
            else -> throw IllegalStateException("Unsupported lunar dialog preference")
        }
    }

    private fun singleChoice(
        pref: ListPreference,
        builder: MaterialAlertDialogBuilder,
    ): Dialog {
        val entries = pref.entries
        val values = pref.entryValues.map { it.toString() }
        builder.setSingleChoiceItems(
            entries,
            pref.findIndexOfValue(pref.value)
        ) { dialog, which ->
            val value = values[which]
            // Same order as ListPreferenceDialogFragmentCompat: ask the listener, then save.
            if (pref.callChangeListener(value)) {
                pref.value = value
            }
            dialog.dismiss()
        }
        // Single choice closes on tap, so there is no Ok button; Cancel stays.
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
            val changedNow = if (isChecked) {
                pendingValues.add(values[which])
            } else {
                pendingValues.remove(values[which])
            }
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
        const val TAG = "lunar_choice_dialog"
        private const val ARG_KEY = "lunar_choice_key"
        private const val STATE_VALUES = "lunar_choice_values"
        private const val STATE_CHANGED = "lunar_choice_changed"

        fun newInstance(key: String): LunarChoiceDialogFragment =
            LunarChoiceDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
    }
}
