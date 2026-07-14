package com.android.calendar.shift

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

        // Material 3 Selection State
        if (position == selectedPosition) {
            holder.card.setCardBackgroundColor(holder.itemView.context.getColor(androidx.appcompat.R.color.material_grey_300))
            holder.card.strokeWidth = 4
            holder.card.strokeColor = holder.itemView.context.getColor(androidx.appcompat.R.color.highlighted_text_material_light)
        } else {
            holder.card.setCardBackgroundColor(holder.itemView.context.getColor(android.R.color.transparent))
            holder.card.strokeWidth = 1
            holder.card.strokeColor = holder.itemView.context.getColor(androidx.appcompat.R.color.material_grey_100)
        }

        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            onPresetSelected(preset)
        }

        holder.editButton.setOnClickListener {
            onEditPreset(preset)
        }
    }

    override fun getItemCount() = presets.size

    fun updatePresets(newPresets: List<ShiftPreset>) {
        presets = newPresets
        notifyDataSetChanged()
    }

    fun getSelectedPreset(): ShiftPreset? {
        return if (selectedPosition in presets.indices) presets[selectedPosition] else null
    }
}
