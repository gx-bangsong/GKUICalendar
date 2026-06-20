package com.android.calendar.shift

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import com.android.calendar.month.MonthWeekEventsView
import java.util.HashSet

class ShiftMonthWeekView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MonthWeekEventsView(context) {

    private val selectedJulianDays = HashSet<Int>()
    private var selectionColor: Int = 0x660000FF.toInt() // 40% opacity blue default

    private val selectionPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun setSelectedDays(julianDays: Set<Int>, color: Int) {
        selectedJulianDays.clear()
        selectedJulianDays.addAll(julianDays)
        selectionColor = (color and 0x00FFFFFF) or 0x66000000
        selectionPaint.color = selectionColor
        borderPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSelection(canvas)
    }

    private fun drawSelection(canvas: Canvas) {
        for (i in 0 until mNumDays) {
            val julianDay = mFirstJulianDay + i
            if (selectedJulianDays.contains(julianDay)) {
                val x = i * (width - mPadding * 2) / mNumDays + mPadding
                val r = (i + 1) * (width - mPadding * 2) / mNumDays + mPadding

                // Draw translucent overlay
                canvas.drawRect(
                    x.toFloat(),
                    0f,
                    r.toFloat(),
                    height.toFloat(),
                    selectionPaint
                )

                // Draw border
                canvas.drawRect(
                    x.toFloat() + 2f,
                    2f,
                    r.toFloat() - 2f,
                    height.toFloat() - 2f,
                    borderPaint
                )
            }
        }
    }
}
