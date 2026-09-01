package com.android.calendar.shift

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewAnimator
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ws.xsoh.etar.R

class ShiftRuleWizardDialogFragment : DialogFragment() {
    private var currentStep = 0
    private var anchorJulianDay = Time.getJulianDay(System.currentTimeMillis(), 0)
    private var allPresets: List<ShiftPreset> = emptyList()
    private val pattern = mutableListOf<ShiftPreset?>()
    private var patternInitialized = false
    private lateinit var animator: ViewAnimator
    private lateinit var stepLabel: TextView
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var txtSelectedDate: TextView
    private lateinit var rotationGrid: RecyclerView
    private lateinit var rotationAdapter: RotationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            allPresets = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().getAllPresets().first()
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
        rotationGrid = view.findViewById(R.id.wizard_rotation_grid)
        rotationAdapter = RotationAdapter()
        rotationGrid.layoutManager = GridLayoutManager(requireContext(), 7)
        rotationGrid.adapter = rotationAdapter
        updateDateDisplay()

        view.findViewById<MaterialButton>(R.id.btn_wizard_pick_date).setOnClickListener {
            MaterialDatePicker.Builder.datePicker().build().apply {
                addOnPositiveButtonClickListener { selection ->
                    anchorJulianDay = Time.getJulianDay(selection, 0)
                    updateDateDisplay()
                }
                show(childFragmentManager, "date_picker")
            }
        }
        view.findViewById<MaterialButton>(R.id.btn_wizard_add_day).setOnClickListener {
            pattern.add(null)
            rotationAdapter.notifyItemInserted(pattern.lastIndex)
        }
        view.findViewById<MaterialButton>(R.id.btn_wizard_clear_pattern).setOnClickListener {
            pattern.clear()
            patternInitialized = false
            ensurePattern()
            rotationAdapter.notifyDataSetChanged()
        }
        btnBack.setOnClickListener { moveStep(-1) }
        btnNext.setOnClickListener { if (currentStep == 2) generateRule() else moveStep(1) }
    }

    private fun moveStep(delta: Int) {
        currentStep = (currentStep + delta).coerceIn(0, 2)
        if (currentStep == 2) {
            ensurePattern()
            rotationAdapter.notifyDataSetChanged()
        }
        animator.displayedChild = currentStep
        btnBack.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
        btnNext.text = if (currentStep == 2) getString(R.string.shift_wizard_generate) else getString(R.string.shift_wizard_next)
        stepLabel.text = when (currentStep) {
            0 -> getString(R.string.shift_wizard_step1)
            1 -> getString(R.string.shift_wizard_step2)
            else -> getString(R.string.shift_wizard_step3)
        }
    }

    private fun ensurePattern() {
        if (patternInitialized) return
        val view = requireView()
        val on = view.findViewById<TextInputEditText>(R.id.edit_days_on).text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 4
        val off = view.findViewById<TextInputEditText>(R.id.edit_days_off).text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 2
        pattern.clear()
        repeat(on) { pattern.add(allPresets.firstOrNull()) }
        repeat(off) { pattern.add(null) }
        patternInitialized = true
    }

    private fun updateDateDisplay() {
        val t = Time().apply { setJulianDay(anchorJulianDay) }
        txtSelectedDate.text = String.format("%04d-%02d-%02d", t.year, t.month + 1, t.day)
    }

    private fun generateRule() {
        ensurePattern()
        if (pattern.isEmpty() || pattern.all { it == null }) {
            Toast.makeText(requireContext(), "Add at least one shift day", Toast.LENGTH_SHORT).show()
            return
        }
        val ids = pattern.map { it?.id ?: 0L }.joinToString(",")
        lifecycleScope.launch {
            ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
                .updateActiveRule(ShiftRotationRule(anchorJulianDay = anchorJulianDay, patternPresetIds = ids))
            dismiss()
        }
    }

    private fun showPresetPicker(position: Int) {
        val names = arrayOf(getString(R.string.shift_rest)) + allPresets.map { it.title }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("第 ${position + 1} 天")
            .setItems(names) { _, which ->
                pattern[position] = if (which == 0) null else allPresets[which - 1]
                rotationAdapter.notifyItemChanged(position)
            }.show()
    }

    private inner class RotationAdapter : RecyclerView.Adapter<RotationAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val label: TextView = view.findViewById(R.id.day_label)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.rotation_grid_item, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val preset = pattern[position]
            holder.label.text = if (preset == null) "第${position + 1}天\n${getString(R.string.shift_rest)}"
            else "第${position + 1}天\n${preset.title}"
            holder.label.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.label, com.google.android.material.R.attr.colorOnSurface))
            holder.card.setCardBackgroundColor(preset?.color?.let { ColorUtils.setAlphaComponent(it, 55) }
                ?: com.google.android.material.color.MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorSurfaceContainer))
            holder.card.strokeColor = preset?.color ?: com.google.android.material.color.MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOutlineVariant)
            holder.itemView.setOnClickListener { showPresetPicker(position) }
        }
        override fun getItemCount() = pattern.size
    }
}
