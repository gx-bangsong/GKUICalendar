/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.month

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for the seven-column month grid used by the month-and-agenda layout of the month entry.
 * The adapter owns cell geometry; each cell renders itself in [MonthDayCellView].
 * Event details are shown in the companion day list, so cells only carry colors.
 */
class MonthGridAdapter(
    private val onDayClick: (Int) -> Unit
) : RecyclerView.Adapter<MonthGridAdapter.CellHolder>() {

    data class Cell(
        val julianDay: Int,
        val dayOfMonth: Int,
        val currentMonth: Boolean,
        val today: Boolean,
        val selected: Boolean,
        val lunarText: String?,
        val eventColors: List<Int>,
        val description: String,
        /** True when the lunar row is shown, so it keeps its space even while the text loads. */
        val lunarEnabled: Boolean = false
    )

    class CellHolder(val view: MonthDayCellView) : RecyclerView.ViewHolder(view)

    private var cells: List<Cell> = emptyList()
    private var cellHeightPx: Int = 0

    /** Replaces all cells. [newCellHeightPx] is the height of every row. */
    fun submitCells(newCells: List<Cell>, newCellHeightPx: Int) {
        cells = newCells
        cellHeightPx = newCellHeightPx
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellHolder =
        CellHolder(MonthDayCellView(parent.context))

    override fun onBindViewHolder(holder: CellHolder, position: Int) {
        val cell = cells[position]
        holder.view.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT, cellHeightPx
        )
        holder.view.bind(cell, cellHeightPx)
        holder.view.setOnClickListener { onDayClick(cell.julianDay) }
    }

    override fun getItemCount(): Int = cells.size
}
