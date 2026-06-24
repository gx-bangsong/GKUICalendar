package com.android.calendar.shift

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftPreset
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ws.xsoh.etar.R

class ShiftRotationTemplateDialogFragment : DialogFragment() {

    private val pattern = mutableListOf<ShiftPreset?>()
    var onPatternConfirmed: ((List<ShiftPreset?>) -> Unit)? = null
    private lateinit var adapter: RotationGridAdapter
    private var allPresets: List<ShiftPreset> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            allPresets = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().getAllPresets().first()
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

        view.findViewById<android.widget.Button>(R.id.btn_add_day).setOnClickListener {
            pattern.add(null) // Default to Rest
            adapter.notifyItemInserted(pattern.size - 1)
        }

        view.findViewById<android.widget.Button>(R.id.btn_clear_rule).setOnClickListener {
            pattern.clear()
            adapter.notifyDataSetChanged()
        }

        view.findViewById<android.widget.Button>(R.id.btn_save_rule).setOnClickListener {
             onPatternConfirmed?.invoke(pattern)
             dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setTitle(getString(R.string.shift_rotation_rule))
        return dialog
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
                holder.card.setCardBackgroundColor(0x339E9E9E.toInt())
                holder.card.strokeColor = 0xFF9E9E9E.toInt()
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
