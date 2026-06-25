package com.android.calendar.shift

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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
        drawSelection(canvas)
        super.onDraw(canvas)
    }

    private fun drawSelection(canvas: Canvas) {
        val days = selectedDays ?: return
        for (i in 0 until mNumDays) {
            val julianDay = mFirstJulianDay + i
            val color = days[julianDay] ?: continue

            selectionPaint.color = (color and 0x00FFFFFF) or 0x44000000

            val r = (mWidth - mPadding * 2) / mNumDays
            val x = (2 * i + 1) * r / 2 + mPadding
            val y = mHeight / 2

            val radius = Math.min(r, mHeight) / 2 - 2
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), selectionPaint)

            selectionPaint.color = color or 0xFF000000.toInt()
            canvas.drawCircle(x.toFloat(), (mHeight - 10).toFloat(), 6f, selectionPaint)
        }
    }
}
