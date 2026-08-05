package com.android.calendar.shift

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewAnimator
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ws.xsoh.etar.R

class ShiftRuleWizardDialogFragment : DialogFragment() {
    private var currentStep = 0
    private var anchorJulianDay: Int = Time.getJulianDay(System.currentTimeMillis(), 0)
    private var allPresets: List<ShiftPreset> = emptyList()
    private lateinit var animator: ViewAnimator
    private lateinit var stepLabel: TextView
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var txtSelectedDate: TextView
    private lateinit var presetChoices: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            allPresets = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().getAllPresets().first()
            if (::presetChoices.isInitialized) updatePresetChoices()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_shift_wizard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        animator = view.findViewById(R.id.wizard_animator)
        stepLabel = view.findViewById(R.id.wizard_step_label)
        btnBack = view.findViewById(R.id.btn_wizard_back)
        btnNext = view.findViewById(R.id.btn_wizard_next)
        txtSelectedDate = view.findViewById(R.id.txt_selected_date)
        presetChoices = view.findViewById(R.id.wizard_preset_choices)
        updateDateDisplay()
        updatePresetChoices()

        view.findViewById<MaterialButton>(R.id.btn_wizard_pick_date).setOnClickListener {
            MaterialDatePicker.Builder.datePicker().build().apply {
                addOnPositiveButtonClickListener { selection ->
                    anchorJulianDay = Time.getJulianDay(selection, 0)
                    updateDateDisplay()
                }
                show(childFragmentManager, "date_picker")
            }
        }
        btnBack.setOnClickListener { moveStep(-1) }
        btnNext.setOnClickListener { if (currentStep == 2) generateRule() else moveStep(1) }
    }

    private fun updatePresetChoices() {
        if (!::presetChoices.isInitialized) return
        presetChoices.removeAllViews()
        allPresets.forEachIndexed { index, preset ->
            val checkBox = MaterialCheckBox(requireContext()).apply {
                text = preset.title
                isChecked = index == 0
                tag = preset.id
                buttonTintList = android.content.res.ColorStateList.valueOf(preset.color)
            }
            presetChoices.addView(checkBox)
        }
    }

    private fun moveStep(delta: Int) {
        currentStep = (currentStep + delta).coerceIn(0, 2)
        animator.displayedChild = currentStep
        btnBack.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
        btnNext.text = if (currentStep == 2) getString(R.string.shift_wizard_generate) else getString(R.string.shift_wizard_next)
        stepLabel.text = when (currentStep) {
            0 -> getString(R.string.shift_wizard_step1)
            1 -> getString(R.string.shift_wizard_step2)
            else -> getString(R.string.shift_wizard_step3)
        }
    }

    private fun updateDateDisplay() {
        val t = Time().apply { setJulianDay(anchorJulianDay) }
        txtSelectedDate.text = String.format("%04d-%02d-%02d", t.year, t.month + 1, t.day)
    }

    private fun generateRule() {
        val view = requireView()
        val daysOn = view.findViewById<TextInputEditText>(R.id.edit_days_on).text.toString().toIntOrNull() ?: 4
        val daysOff = view.findViewById<TextInputEditText>(R.id.edit_days_off).text.toString().toIntOrNull() ?: 2
        val selectedIds = (0 until presetChoices.childCount)
            .map { presetChoices.getChildAt(it) }
            .filterIsInstance<MaterialCheckBox>()
            .filter { it.isChecked }
            .map { it.tag as Long }
        if (daysOn <= 0 || daysOff < 0 || selectedIds.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one preset and a valid pattern", Toast.LENGTH_SHORT).show()
            return
        }
        val pattern = mutableListOf<Long>()
        repeat(daysOn) { pattern.add(selectedIds[it % selectedIds.size]) }
        repeat(daysOff) { pattern.add(0L) }
        lifecycleScope.launch {
            ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
                .updateActiveRule(ShiftRotationRule(anchorJulianDay = anchorJulianDay, patternPresetIds = pattern.joinToString(",")))
            dismiss()
        }
    }
}
