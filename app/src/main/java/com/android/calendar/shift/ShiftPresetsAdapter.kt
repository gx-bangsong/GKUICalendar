package com.android.calendar.shift

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.shift.db.ShiftPreset
import ws.xsoh.etar.R

class ShiftPresetsAdapter(
    private var presets: List<ShiftPreset>,
    private val onPresetSelected: (ShiftPreset) -> Unit,
    private val onEditPreset: (ShiftPreset) -> Unit
) : RecyclerView.Adapter<ShiftPresetsAdapter.ViewHolder>() {

    private var selectedPosition = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

        holder.itemView.isSelected = position == selectedPosition
        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            Log.e("ShiftDebug", "PRESETS_ADAPTER: Selected ${preset.title}"); onPresetSelected(preset)
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
