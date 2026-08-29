/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.calendar.lunar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.view.View
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import ws.xsoh.etar.R

/**
 * Draws the contextual lunar layer of one month-view day cell.
 *
 * Two [TextPaint]s are shared by every cell (and every week view); theme
 * colors are resolved per draw call so day/night switches need no rebuild.
 * MD3 tokens are used directly: `colorPrimaryContainer` /
 * `colorOnPrimaryContainer` for the festival chip, `colorOnSurfaceVariant`
 * for the plain lunar text.
 */
object LunarDayRenderer {

    /** State B (VISIBLE): solar number + 11sp lunar text. */
    private const val TEXT_SIZE_SP = 11f

    /** State C (EMPHASIZED): 13sp bold festival name on a chip. */
    private const val FESTIVAL_TEXT_SIZE_SP = 13f

    private const val LINE_GAP_DP = 2f

    private val chipRect = RectF()
    private val chipPaint = Paint()
    private val textPaint = TextPaint()
    private val festivalPaint = TextPaint()
    private var cornerRadius = 0f
    private var lineGap = 0f
    private var initialized = false

    /**
     * Draws the festival chip behind the solar day number (mirrors the
     * today-pill geometry) and retints the number to sit on the chip.
     */
    @JvmStatic
    fun drawFestivalChip(
        canvas: Canvas, view: View, cx: Float, baseline: Float,
        ascentHeight: Float, lineHeight: Float, number: String, numberPaint: Paint
    ) {
        init(view.context)
        val halfWidth = numberPaint.measureText(number) / 2f + 10f
        chipRect.set(
            cx - halfWidth, baseline - ascentHeight,
            cx + halfWidth, baseline + (lineHeight - ascentHeight)
        )
        chipPaint.color = MaterialColors.getColor(view, MaterialR.attr.colorPrimaryContainer)
        canvas.drawRoundRect(chipRect, cornerRadius, cornerRadius, chipPaint)
        numberPaint.color = MaterialColors.getColor(view, MaterialR.attr.colorOnPrimaryContainer)
    }

    /**
     * Draws the lunar text below the solar day number: the festival name in
     * bold when emphasized, otherwise the compact lunar day label.
     */
    @JvmStatic
    fun drawLunarText(
        canvas: Canvas, view: View, cx: Float, baseline: Float,
        lineHeight: Float, info: LunarInfo, isToday: Boolean
    ) {
        init(view.context)
        val paint = if (info.isFestival) festivalPaint else textPaint
        paint.color = when {
            info.isFestival && isToday ->
                MaterialColors.getColor(view, MaterialR.attr.colorOnPrimary)
            info.isFestival ->
                MaterialColors.getColor(view, MaterialR.attr.colorOnPrimaryContainer)
            else -> MaterialColors.getColor(view, MaterialR.attr.colorOnSurfaceVariant)
        }
        canvas.drawText(info.shortText, cx, baseline + lineHeight + lineGap, paint)
    }

    private fun init(context: Context) {
        if (initialized) return
        val density = context.resources.displayMetrics.density
        cornerRadius = context.resources.getDimension(R.dimen.month_event_corner_radius)
        lineGap = LINE_GAP_DP * density
        textPaint.isAntiAlias = true
        textPaint.textSize = TEXT_SIZE_SP * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
        festivalPaint.isAntiAlias = true
        festivalPaint.textSize = FESTIVAL_TEXT_SIZE_SP * density
        festivalPaint.textAlign = Paint.Align.CENTER
        festivalPaint.style = Paint.Style.FILL
        festivalPaint.isFakeBoldText = true
        chipPaint.style = Paint.Style.FILL
        chipPaint.isAntiAlias = true
        initialized = true
    }
}
