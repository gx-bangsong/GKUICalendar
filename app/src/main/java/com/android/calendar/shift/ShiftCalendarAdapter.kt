package com.android.calendar.shift

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.shift.db.ShiftPreset
import com.google.android.material.card.MaterialCardView
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

    fun setDays(newDays: List<DayCell>) {
        daysList = newDays
        notifyDataSetChanged()
    }

    fun setPaintMode(enabled: Boolean) {
        isPaintMode = enabled
    }

    fun getDays(): List<DayCell> = daysList

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: MaterialCardView = view.findViewById(R.id.day_card_root)
        val dayText: TextView = view.findViewById(R.id.txt_day_number)
        val shiftDot: View = view.findViewById(R.id.view_shift_dot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.shift_day_grid_item, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val cell = daysList[position]
        holder.dayText.text = cell.dayOfMonth.toString()

        // 1. Theme Color Utilities
        val primaryColor = getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)
        val textColorPrimary = getThemeColor(context, android.R.attr.textColorPrimary)
        val textColorSecondary = getThemeColor(context, android.R.attr.textColorSecondary)

        // 2. Base text and alpha defaults
        val baseAlpha = if (cell.isCurrentMonth) 1.0f else 0.35f
        holder.dayText.alpha = baseAlpha
        holder.shiftDot.alpha = baseAlpha

        // Reset backgrounds and borders
        holder.cardRoot.setCardBackgroundColor(Color.TRANSPARENT)
        holder.cardRoot.strokeWidth = 0
        holder.dayText.setBackgroundResource(0)

        // 3. Selection state has highest priority
        if (cell.isSelected) {
            // MD3 Filled circular background on dayText
            holder.dayText.setBackgroundResource(R.drawable.circle)
            holder.dayText.backgroundTintList = ColorStateList.valueOf(primaryColor)
            holder.dayText.setTextColor(Color.WHITE)
            holder.dayText.alpha = 1.0f
        } else {
            holder.dayText.setTextColor(if (cell.isCurrentMonth) textColorPrimary else textColorSecondary)
            if (cell.isToday) {
                // Today has a clean outline border on dayText or Card
                holder.cardRoot.strokeColor = primaryColor
                holder.cardRoot.strokeWidth = 3 // 1.5dp in standard pixels
            }
        }

        // 4. Shift custom preset coloring
        if (cell.preset != null) {
            holder.shiftDot.visibility = View.VISIBLE
            holder.shiftDot.backgroundTintList = ColorStateList.valueOf(cell.preset.color)

            // If NOT selected, we also give the card a soft translucent 15% opacity background tint
            if (!cell.isSelected) {
                val color = cell.preset.color
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val softBg = Color.argb(40, r, g, b) // 15% opacity
                holder.cardRoot.setCardBackgroundColor(softBg)
            }
        } else {
            holder.shiftDot.visibility = View.INVISIBLE
        }

        holder.itemView.setOnClickListener {
            if (isPaintMode) {
                onDayPainted(cell)
            } else {
                onDayClicked(cell)
            }
        }
    }

    override fun getItemCount(): Int = daysList.size

    private fun getThemeColor(context: Context, attr: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
