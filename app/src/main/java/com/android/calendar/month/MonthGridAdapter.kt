package com.android.calendar.month

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.Event
import java.util.Calendar

/**
 * Adapter for the seven-column month grid. The adapter owns cell positioning;
 * event rendering remains inside MonthDayCellView.
 */
class MonthGridAdapter(
    private val onDayClick: (Int) -> Unit
) : RecyclerView.Adapter<MonthGridAdapter.CellHolder>() {
    data class Cell(
        val julianDay: Int,
        val dayOfMonth: Int,
        val currentMonth: Boolean,
        val today: Boolean,
        val events: List<Event>
    )

    private var cells: List<Cell> = emptyList()

    class CellHolder(val view: MonthDayCellView) : RecyclerView.ViewHolder(view)

    fun submitCells(newCells: List<Cell>) {
        cells = newCells
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellHolder =
        CellHolder(MonthDayCellView(parent.context))

    override fun onBindViewHolder(holder: CellHolder, position: Int) {
        val cell = cells[position]
        holder.view.bind(cell.dayOfMonth, cell.currentMonth, cell.today, cell.events)
        holder.itemView.setOnClickListener { onDayClick(cell.julianDay) }
    }

    override fun getItemCount(): Int = cells.size
}
