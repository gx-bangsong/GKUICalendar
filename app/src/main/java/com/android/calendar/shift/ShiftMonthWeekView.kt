package com.android.calendar.shift

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import com.android.calendar.month.MonthWeekEventsView

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
        // Redraw week numbers and separators on top of selection backgrounds for maximum readability
        drawWeekNums(canvas)
        drawDaySeparators(canvas)
    }

    private fun drawSelection(canvas: Canvas) {
        val days = selectedDays ?: return
        if (days.isEmpty()) return

        val startCell = if (mShowWeekNum) 1 else 0

        for (dayOffset in 0 until mNumDays) {
            val julianDay = mFirstJulianDay + dayOffset
            val color = days[julianDay] ?: continue

            val cellIndex = startCell + dayOffset
            val x = (2 * cellIndex + 1) * (mWidth - mPadding * 2) / (2 * mNumCells) + mPadding
            val y = mHeight / 2

            // Soft translucent background (opacity = 140 / 255 ≈ 55%)
            selectionPaint.color = Color.argb(140, Color.red(color), Color.green(color), Color.blue(color))

            val cellWidth = (mWidth - mPadding * 2).toFloat() / mNumCells
            val radius = Math.min(cellWidth, mHeight.toFloat()) * 0.35f

            // Draw the shift highlight circle
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius, selectionPaint)
        }
    }
}
