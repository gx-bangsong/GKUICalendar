package com.android.calendar.shift

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ws.xsoh.etar.R

class ShiftRotationTemplateDialogFragment : DialogFragment() {

    private val pattern = mutableListOf<ShiftPreset?>()
    private lateinit var adapter: RotationGridAdapter
    private var allPresets: List<ShiftPreset> = emptyList()
    private var anchorJulianDay: Int = 0

    companion object {
        fun newInstance(anchorJulianDay: Int): ShiftRotationTemplateDialogFragment {
            val f = ShiftRotationTemplateDialogFragment()
            f.anchorJulianDay = anchorJulianDay
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (pattern.isEmpty()) {
            repeat(7) { pattern.add(null) }
        }
        lifecycleScope.launch {
            val db = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
            allPresets = db.getAllPresets().first()

            // Try to load existing rule to pre-fill
            val activeRule = db.getActiveRule().first()
            if (activeRule != null && activeRule.patternPresetIds.isNotEmpty()) {
                pattern.clear()
                val ids = activeRule.patternPresetIds.split(",").map { it.toLong() }
                for (id in ids) {
                    pattern.add(if (id == 0L) null else allPresets.find { it.id == id })
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_rotation_template, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val grid = view.findViewById<RecyclerView>(R.id.rotation_grid)

        adapter = RotationGridAdapter()
        grid.layoutManager = GridLayoutManager(requireContext(), 7)
        grid.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btn_add_day).setOnClickListener {
            pattern.add(null)
            adapter.notifyItemInserted(pattern.size - 1)
            grid.smoothScrollToPosition(pattern.size - 1)
        }

        view.findViewById<MaterialButton>(R.id.btn_clear_rule).setOnClickListener {
            pattern.clear()
            repeat(7) { pattern.add(null) }
            adapter.notifyDataSetChanged()
        }

        view.findViewById<MaterialButton>(R.id.btn_save_rule).setOnClickListener {
            saveRuleToDb()
        }
    }

    private fun saveRuleToDb() {
        val ids = pattern.map { it?.id ?: 0L }.joinToString(",")
        val rule = ShiftRotationRule(anchorJulianDay = anchorJulianDay, patternPresetIds = ids)
        lifecycleScope.launch {
            ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().updateActiveRule(rule)
            dismiss()
        }
    }

    inner class RotationGridAdapter : RecyclerView.Adapter<RotationGridAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val label: TextView = view.findViewById(R.id.day_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.rotation_grid_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val preset = pattern[position]
            if (preset == null) {
                holder.label.text = getString(R.string.shift_rest)
                holder.card.setCardBackgroundColor(0x11888888.toInt())
                holder.card.strokeColor = 0x44888888.toInt()
            } else {
                holder.label.text = preset.title
                val color = preset.color
                holder.card.setCardBackgroundColor((color and 0x00FFFFFF) or 0x33000000)
                holder.card.strokeColor = color
            }

            holder.itemView.setOnClickListener {
                showPresetPicker(position)
            }
        }

        override fun getItemCount() = pattern.size
    }

    private fun showPresetPicker(position: Int) {
        val names = mutableListOf<String>()
        names.add(getString(R.string.shift_rest))
        names.addAll(allPresets.map { it.title })

        AlertDialog.Builder(requireContext())
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    pattern[position] = null
                } else {
                    pattern[position] = allPresets[which - 1]
                }
                adapter.notifyItemChanged(position)
            }
            .show()
    }
}
