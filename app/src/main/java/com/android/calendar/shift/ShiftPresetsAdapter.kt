package com.android.calendar.shift

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.shift.db.ShiftPreset
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.shift_preset_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val preset = presets[position]
        val context = holder.itemView.context
        val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface)
        val onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant)

        holder.title.text = preset.title
        holder.time.text = String.format("%02d:%02d - %02d:%02d",
            preset.startTime / 60, preset.startTime % 60,
            preset.endTime / 60, preset.endTime % 60)
        holder.colorBar.backgroundTintList = ColorStateList.valueOf(preset.color)
        holder.title.setTextColor(onSurface)
        holder.time.setTextColor(onSurfaceVariant)
        holder.card.strokeWidth = 2
        if (position == selectedPosition) {
            holder.card.setCardBackgroundColor(ColorUtils.setAlphaComponent(preset.color, 36))
            holder.card.strokeColor = preset.color
        } else {
            holder.card.setCardBackgroundColor(surface)
            holder.card.strokeColor = outlineVariant
        }
        holder.itemView.setOnClickListener { selectPosition(holder.bindingAdapterPosition) }
        holder.editButton.setOnClickListener { onEditPreset(preset) }
    }

    private fun selectPosition(position: Int) {
        if (position !in presets.indices) return
        val oldPos = selectedPosition
        selectedPosition = position
        if (oldPos >= 0) notifyItemChanged(oldPos)
        notifyItemChanged(selectedPosition)
        onPresetSelected(presets[selectedPosition])
    }

    override fun getItemCount() = presets.size

    fun updatePresets(newPresets: List<ShiftPreset>) {
        presets = newPresets
        if (selectedPosition == -1 && presets.isNotEmpty()) selectedPosition = 0
        else if (selectedPosition >= presets.size) selectedPosition = if (presets.isNotEmpty()) 0 else -1
        notifyDataSetChanged()
    }

    fun getSelectedPreset(): ShiftPreset? =
        if (selectedPosition in presets.indices) presets[selectedPosition] else null
}
