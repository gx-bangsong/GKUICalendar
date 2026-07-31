package com.android.calendar.shift

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.shift.db.ShiftPreset
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import ws.xsoh.etar.R
import java.util.*

data class DayCell(
    val dayOfMonth: Int,
    val julianDay: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val preset: ShiftPreset?,
    val calendar: Calendar
)

class ShiftCalendarAdapter(
    private val context: Context,
    private val onDayClicked: (DayCell) -> Unit,
    private val onDayPainted: (DayCell) -> Unit
) : RecyclerView.Adapter<ShiftCalendarAdapter.DayViewHolder>() {
    private var daysList = emptyList<DayCell>()
    private var isPaintMode = false

    fun setDays(newDays: List<DayCell>) { daysList = newDays; notifyDataSetChanged() }
    fun setPaintMode(enabled: Boolean) { isPaintMode = enabled }
    fun getDays(): List<DayCell> = daysList

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: MaterialCardView = view.findViewById(R.id.day_card_root)
        val dayText: TextView = view.findViewById(R.id.txt_day_number)
        val shiftDot: View = view.findViewById(R.id.view_shift_dot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder =
        DayViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.shift_day_grid_item, parent, false))

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val cell = daysList[position]
        val surface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurface)
        val onSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val primary = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimary)
        val outlineVariant = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOutlineVariant)

        // RecyclerView items must not retain state from a previously bound cell.
        holder.cardRoot.setCardBackgroundColor(surface)
        holder.cardRoot.strokeWidth = 0
        holder.cardRoot.strokeColor = outlineVariant
        holder.dayText.setBackgroundResource(0)
        holder.dayText.backgroundTintList = null
        holder.dayText.setTextColor(onSurface)
        holder.dayText.alpha = if (cell.isCurrentMonth) 1f else .55f
        holder.shiftDot.visibility = View.INVISIBLE
        holder.shiftDot.alpha = 1f
        holder.shiftDot.backgroundTintList = null

        holder.dayText.text = cell.dayOfMonth.toString()
        if (cell.isSelected) {
            holder.dayText.setBackgroundResource(R.drawable.circle)
            holder.dayText.backgroundTintList = ColorStateList.valueOf(primary)
            holder.dayText.setTextColor(onPrimary)
            holder.dayText.alpha = 1f
        } else if (cell.isToday) {
            holder.cardRoot.strokeColor = primary
            holder.cardRoot.strokeWidth = 2
            holder.dayText.setTextColor(onSurface)
        } else if (!cell.isCurrentMonth) {
            holder.dayText.setTextColor(onSurfaceVariant)
        }

        cell.preset?.let { preset ->
            holder.shiftDot.visibility = View.VISIBLE
            holder.shiftDot.backgroundTintList = ColorStateList.valueOf(preset.color)
            if (!cell.isSelected) {
                holder.cardRoot.setCardBackgroundColor(ColorUtils.setAlphaComponent(preset.color, 40))
            }
        }

        holder.itemView.setOnClickListener {
            if (isPaintMode) onDayPainted(cell) else onDayClicked(cell)
        }
    }

    override fun getItemCount(): Int = daysList.size
}
