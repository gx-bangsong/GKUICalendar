package com.android.calendar.shift

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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
        Log.d("ShiftDebug", "drawSelection: view ${this.hashCode()} week $mWeek, range $mFirstJulianDay to ${mFirstJulianDay + mNumDays}")

        for (i in 0 until mNumDays) {
            val julianDay = mFirstJulianDay + i
            val color = days[julianDay] ?: continue

            Log.d("ShiftDebug", "drawSelection: view ${this.hashCode()} MATCH JD=$julianDay color=$color")

            // Draw a semi-transparent circular background
            selectionPaint.color = (color and 0x00FFFFFF) or 0x66000000

            val r = (mWidth - mPadding * 2) / mNumDays
            val x = (2 * i + 1) * r / 2 + mPadding
            val y = mHeight / 2

            val radius = Math.min(r, mHeight) / 2 - 4
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), selectionPaint)

            // Draw a solid dot at the bottom
            selectionPaint.color = color or 0xFF000000.toInt()
            canvas.drawCircle(x.toFloat(), (mHeight - 12).toFloat(), 8f, selectionPaint)
        }
    }
}
