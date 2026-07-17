package com.android.calendar.shift

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.shift.db.ShiftPreset
import com.google.android.material.card.MaterialCardView
import ws.xsoh.etar.R

class ShiftPresetsAdapter(
    private var presets: List<ShiftPreset>,
    private val onPresetSelected: (ShiftPreset) -> Unit,
    private val onEditPreset: (ShiftPreset) -> Unit
) : RecyclerView.Adapter<ShiftPresetsAdapter.ViewHolder>() {

    private var selectedPosition = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.preset_card)
        val colorBar: View = view.findViewById(R.id.preset_color_bar)
        val title: TextView = view.findViewById(R.id.preset_title)
        val time: TextView = view.findViewById(R.id.preset_time)
        val editButton: ImageButton = view.findViewById(R.id.edit_preset_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.shift_preset_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val preset = presets[position]
        holder.title.text = preset.title
        holder.time.text = String.format("%02d:%02d - %02d:%02d",
            preset.startTime / 60, preset.startTime % 60,
            preset.endTime / 60, preset.endTime % 60)

        holder.colorBar.setBackgroundColor(preset.color)

        if (position == selectedPosition) {
            val r = android.graphics.Color.red(preset.color)
            val g = android.graphics.Color.green(preset.color)
            val b = android.graphics.Color.blue(preset.color)
            holder.card.setCardBackgroundColor(android.graphics.Color.argb(40, r, g, b)) // ~15% opacity of preset color
            holder.card.strokeWidth = 6
            holder.card.strokeColor = preset.color
        } else {
            holder.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            holder.card.strokeWidth = 2
            holder.card.strokeColor = 0x40808080.toInt() // Subtle grey with alpha, theme-agnostic!
        }

        holder.itemView.setOnClickListener {
            selectPosition(holder.adapterPosition)
        }

        holder.editButton.setOnClickListener {
            onEditPreset(preset)
        }
    }

    private fun selectPosition(position: Int) {
        if (position !in presets.indices) return
        val oldPos = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPos)
        notifyItemChanged(selectedPosition)
        onPresetSelected(presets[selectedPosition])
        Log.e("ShiftDebug", "ADAPTER: Preset selected: ${presets[selectedPosition].title}")
    }

    override fun getItemCount() = presets.size

    fun updatePresets(newPresets: List<ShiftPreset>) {
        presets = newPresets
        if (selectedPosition == -1 && presets.isNotEmpty()) {
            selectedPosition = 0
            // Don't call onPresetSelected here to avoid loop, let fragment handle it if needed
        } else if (selectedPosition >= presets.size) {
            selectedPosition = if (presets.isNotEmpty()) 0 else -1
        }
        notifyDataSetChanged()
    }

    fun getSelectedPreset(): ShiftPreset? {
        return if (selectedPosition in presets.indices) presets[selectedPosition] else null
    }
}
