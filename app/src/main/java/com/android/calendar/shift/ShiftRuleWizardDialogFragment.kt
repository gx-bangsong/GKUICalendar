package com.android.calendar.shift

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import com.google.android.material.button.MaterialButton
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
    private lateinit var presetSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            allPresets = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().getAllPresets().first()
            updateSpinner()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_shift_wizard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        animator = view.findViewById(R.id.wizard_animator)
        stepLabel = view.findViewById(R.id.wizard_step_label)
        btnBack = view.findViewById(R.id.btn_wizard_back)
        btnNext = view.findViewById(R.id.btn_wizard_next)
        txtSelectedDate = view.findViewById(R.id.txt_selected_date)
        presetSpinner = view.findViewById(R.id.wizard_preset_spinner)

        updateDateDisplay()

        view.findViewById<MaterialButton>(R.id.btn_wizard_pick_date).setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker().build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                anchorJulianDay = Time.getJulianDay(selection, 0)
                updateDateDisplay()
            }
            datePicker.show(childFragmentManager, "date_picker")
        }

        btnBack.setOnClickListener { moveStep(-1) }
        btnNext.setOnClickListener {
            if (currentStep == 2) {
                generateRule()
            } else {
                moveStep(1)
            }
        }
    }

    private fun moveStep(delta: Int) {
        currentStep += delta
        animator.displayedChild = currentStep
        btnBack.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
        btnNext.text = if (currentStep == 2) getString(R.string.shift_wizard_generate) else getString(R.string.shift_wizard_next)

        stepLabel.text = when(currentStep) {
            0 -> getString(R.string.shift_wizard_step1)
            1 -> getString(R.string.shift_wizard_step2)
            2 -> getString(R.string.shift_wizard_step3)
            else -> ""
        }
    }

    private fun updateDateDisplay() {
        val t = Time()
        t.setJulianDay(anchorJulianDay)
        txtSelectedDate.text = String.format("%04d-%02d-%02d", t.year, t.month + 1, t.day)
    }

    private fun updateSpinner() {
        if (!::presetSpinner.isInitialized) return
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, allPresets.map { it.title })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        presetSpinner.adapter = adapter
    }

    private fun generateRule() {
        val view = requireView()
        val daysOn = view.findViewById<TextInputEditText>(R.id.edit_days_on).text.toString().toIntOrNull() ?: 4
        val daysOff = view.findViewById<TextInputEditText>(R.id.edit_days_off).text.toString().toIntOrNull() ?: 2

        val selectedIdx = presetSpinner.selectedItemPosition
        if (selectedIdx < 0 || allPresets.isEmpty()) {
            Toast.makeText(context, "Please create a preset first", Toast.LENGTH_SHORT).show()
            return
        }
        val presetId = allPresets[selectedIdx].id

        val pattern = mutableListOf<Long>()
        repeat(daysOn) { pattern.add(presetId) }
        repeat(daysOff) { pattern.add(0L) }

        val rule = ShiftRotationRule(anchorJulianDay = anchorJulianDay, patternPresetIds = pattern.joinToString(","))
        lifecycleScope.launch {
            ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().updateActiveRule(rule)
            dismiss()
        }
    }
}
