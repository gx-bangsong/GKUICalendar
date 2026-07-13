package com.android.calendar.shift

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import com.android.calendar.month.MonthWeekEventsView
import android.util.Log

class ShiftMonthWeekView(context: Context) : MonthWeekEventsView(context) {

    private val selectionPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var selectedDays: Map<Int, Int>? = null

    fun setSelection(days: Map<Int, Int>) {
        selectedDays = days
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSelection(canvas)
    }

    private fun drawSelection(canvas: Canvas) {
        val days = selectedDays ?: return
        if (days.isEmpty()) return

        val divisor = 2 * mNumCells
        val startCell = if (mShowWeekNum) 1 else 0

        for (dayOffset in 0 until mNumDays) {
            val julianDay = mFirstJulianDay + dayOffset
            val color = days[julianDay] ?: continue

            Log.e("ShiftDebug", "DRAW: Match found! JD=$julianDay Color=$color Week=$mWeek View=${this.hashCode()}")

            val cellIndex = startCell + dayOffset
            val x = (2 * cellIndex + 1) * mWidth / divisor
            val y = mHeight / 2

            // High visibility opacity (0xCC = 80%)
            selectionPaint.color = Color.argb(200, Color.red(color), Color.green(color), Color.blue(color))

            val cellWidth = mWidth / mNumCells
            val radius = Math.min(cellWidth, mHeight) / 2 - 4
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), selectionPaint)

            // Draw a large solid dot at the bottom
            selectionPaint.color = color or 0xFF000000.toInt()
            canvas.drawCircle(x.toFloat(), (mHeight - 15).toFloat(), 12f, selectionPaint)
        }
    }
}
