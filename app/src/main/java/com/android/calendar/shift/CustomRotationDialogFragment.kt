package com.android.calendar.shift

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import ws.xsoh.etar.R

class CustomRotationDialogFragment : DialogFragment() {

    private val pattern = mutableListOf<Boolean>()
    var onPatternConfirmed: ((List<Boolean>) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_custom_rotation, null)
        val chipGroup = view.findViewById<ChipGroup>(R.id.rule_chip_group)

        view.findViewById<android.widget.Button>(R.id.btn_add_work).setOnClickListener {
            pattern.add(true)
            addChip(chipGroup, true)
        }

        view.findViewById<android.widget.Button>(R.id.btn_add_rest).setOnClickListener {
            pattern.add(false)
            addChip(chipGroup, false)
        }

        view.findViewById<android.widget.Button>(R.id.btn_clear_rule).setOnClickListener {
            pattern.clear()
            chipGroup.removeAllViews()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onPatternConfirmed?.invoke(pattern)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun addChip(group: ChipGroup, isWork: Boolean) {
        val chip = Chip(requireContext())
        chip.text = if (isWork) getString(R.string.shift_work) else getString(R.string.shift_rest)
        chip.isCloseIconVisible = true
        val index = pattern.size - 1
        chip.setOnCloseIconClickListener {
            pattern.removeAt(index)
            group.removeView(chip)
        }
        group.addView(chip)
    }
}
