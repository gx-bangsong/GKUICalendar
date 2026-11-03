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
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import ws.xsoh.etar.R

/**
 * Shows a settings page's [EditTextPreference] in an MD3 dialog.
 *
 * Returns true when the dialog was requested. Returns false for any other preference, which then
 * goes to the AndroidX dialog as before.
 *
 * The AndroidX dialog builds a plain AlertDialog.Builder, so it does not get the MD3 dialog shape.
 * This fragment uses MaterialAlertDialogBuilder and an outlined text field.
 *
 * Behaviour matches EditTextPreferenceDialogFragmentCompat: Ok asks the change listener, and saves
 * the text only if the listener accepts it. Cancel, back and an outside tap change nothing. The
 * dialog is restored by the child FragmentManager, and its text by the dialog state, so rotation
 * keeps what was typed without showing a second dialog.
 */
internal fun PreferenceFragmentCompat.showSettingsTextDialog(preference: Preference): Boolean {
    if (preference !is EditTextPreference) return false
    // After rotation the child FragmentManager restores the dialog itself. Do not show a second.
    if (childFragmentManager.findFragmentByTag(SettingsTextDialogFragment.TAG) == null) {
        SettingsTextDialogFragment.newInstance(preference.key)
            .show(childFragmentManager, SettingsTextDialogFragment.TAG)
    }
    return true
}

internal class SettingsTextDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val pref = preference() as? EditTextPreference
            ?: throw IllegalStateException("No text preference for key ${key()}")

        val view = LayoutInflater.from(requireActivity())
            .inflate(R.layout.settings_text_dialog, null)
        val input = view.findViewById<TextInputEditText>(R.id.settings_text_input)
        if (savedInstanceState == null) {
            val current = pref.text.orEmpty()
            input.setText(current)
            input.setSelection(current.length)
        }

        val dialog = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(pref.dialogTitle ?: pref.title)
            .setView(view)
            .setPositiveButton(pref.positiveButtonText ?: getString(android.R.string.ok)) { _, _ ->
                commit(pref, input.text?.toString().orEmpty())
            }
            .setNegativeButton(pref.negativeButtonText ?: getString(android.R.string.cancel), null)
            .create()
        // Show the keyboard with the field, as the AndroidX text dialog does.
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    private fun commit(pref: EditTextPreference, text: String) {
        if (pref.callChangeListener(text)) {
            pref.text = text
        }
    }

    private fun key(): String = requireArguments().getString(ARG_KEY).orEmpty()

    private fun preference(): Preference? =
        (requireParentFragment() as PreferenceFragmentCompat).findPreference<Preference>(key())

    companion object {
        const val TAG = "settings_text_dialog"
        private const val ARG_KEY = "settings_text_key"

        fun newInstance(key: String): SettingsTextDialogFragment =
            SettingsTextDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
    }
}
