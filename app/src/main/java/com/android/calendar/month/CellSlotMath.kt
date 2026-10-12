/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.month

import kotlin.math.ceil

/**
 * Slot sizes of one day cell, in px. Pure math, with no Android types, so it can run in plain
 * JVM tests. [MonthDayCellView] and the month grid both use these numbers, so the row height the
 * grid asks for is the height the slots take.
 */
object CellSlotMath {
    const val CELL_PADDING_DP = 6
    const val DAY_SLOT_MIN_DP = 28
    const val DAY_TEXT_SP = 14f
    const val LUNAR_TEXT_SP = 11f
    const val LUNAR_MIN_SP = 9f
    const val DOT_SLOT_DP = 5
    const val DOT_TOP_MARGIN_DP = 2
    const val MAX_DOTS = 3
    const val LINE_FACTOR = 1.3f

    /** Day slot: at least the circle, and grows with the day text line at this font scale. */
    @JvmStatic
    fun daySlotPx(density: Float, fontScale: Float): Int =
        ceil(maxOf(DAY_SLOT_MIN_DP * density, DAY_TEXT_SP * fontScale * density * LINE_FACTOR).toDouble()).toInt()

    /** Lunar slot: one text line at this font scale. */
    @JvmStatic
    fun lunarSlotPx(density: Float, fontScale: Float): Int =
        ceil((LUNAR_TEXT_SP * fontScale * density * LINE_FACTOR).toDouble()).toInt()

    /** Smallest row height that shows every slot without clipping at this font scale. */
    @JvmStatic
    fun minRowHeightPx(density: Float, fontScale: Float, lunarEnabled: Boolean): Int {
        val padding = CELL_PADDING_DP * density
        val day = daySlotPx(density, fontScale).toFloat()
        val lunar = if (lunarEnabled) lunarSlotPx(density, fontScale).toFloat() else 0f
        val dots = (DOT_TOP_MARGIN_DP + DOT_SLOT_DP) * density
        return ceil((padding + day + lunar + dots).toDouble()).toInt()
    }
}
