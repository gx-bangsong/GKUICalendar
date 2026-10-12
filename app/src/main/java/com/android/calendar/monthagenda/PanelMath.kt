/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import kotlin.math.abs

/**
 * Pure geometry and snapping rules for the month-agenda handle and month swipe.
 *
 * Progress is 0 for the collapsed month (one row, the selected week) and 1 for the
 * expanded month (all rows). Values in between come from dragging the handle.
 */
object PanelMath {

    @JvmStatic
    fun clamp01(value: Float): Float = when {
        value < 0f -> 0f
        value > 1f -> 1f
        else -> value
    }

    /** Progress after dragging by [dyPx] (positive is down, which expands). */
    @JvmStatic
    fun progressFromDrag(startProgress: Float, dyPx: Float, spanPx: Float): Float {
        if (spanPx <= 0f) return clamp01(startProgress)
        return clamp01(startProgress + dyPx / spanPx)
    }

    /**
     * Snap target (0 collapsed, 1 expanded) after a release. A fast fling wins over
     * position: down expands, up collapses. Otherwise the nearer end wins.
     */
    @JvmStatic
    fun snapTarget(progress: Float, velocityPxPerSec: Float, flingPxPerSec: Float): Float = when {
        velocityPxPerSec > flingPxPerSec -> 1f
        velocityPxPerSec < -flingPxPerSec -> 0f
        progress >= 0.5f -> 1f
        else -> 0f
    }

    /**
     * Effective expanded state. A choice the user made stays in effect. Without one,
     * the window-size rule decides, and that rule is recomputed on every layout pass.
     */
    @JvmStatic
    fun isExpanded(userExpanded: Boolean?, autoCollapsed: Boolean): Boolean =
        userExpanded ?: !autoCollapsed

    /** Auto rule: a body shorter than the threshold starts collapsed. */
    @JvmStatic
    fun isAutoCollapsed(bodyHeightPx: Int, thresholdPx: Int): Boolean = bodyHeightPx < thresholdPx

    /** Height of the grid viewport at [progress]: one row when collapsed, all rows when expanded. */
    @JvmStatic
    fun gridHeightPx(progress: Float, cellPx: Int, rowCount: Int): Int {
        val extraRows = maxOf(0, rowCount - 1)
        return cellPx + (extraRows * cellPx * clamp01(progress)).toInt()
    }

    /** Vertical offset that keeps the selected week visible while the grid is collapsed. */
    @JvmStatic
    fun gridTranslationPx(progress: Float, selectedRow: Int, cellPx: Int): Float =
        -(selectedRow * cellPx).toFloat() * (1f - clamp01(progress))

    /** Row index (0-based) of [julian] in a grid that starts at [firstCellJulian]. */
    @JvmStatic
    fun selectedRow(julian: Int, firstCellJulian: Int, rowCount: Int): Int =
        ((julian - firstCellJulian) / 7).coerceIn(0, maxOf(0, rowCount - 1))

    /** True when the gesture is clearly horizontal: past the slop and well ahead of vertical motion. */
    @JvmStatic
    fun isHorizontalSwipe(dxPx: Float, dyPx: Float, slopPx: Float): Boolean =
        abs(dxPx) > slopPx && abs(dxPx) > abs(dyPx) * 2f

    /**
     * Month step for a finished horizontal swipe: +1 for a leftward swipe (next month),
     * -1 for rightward (previous month), 0 when the gesture was too short or too slow.
     */
    @JvmStatic
    fun swipeMonthDelta(
        dxPx: Float,
        velocityXPxPerSec: Float,
        distanceMinPx: Float,
        flingPxPerSec: Float
    ): Int {
        val leftward = dxPx < 0f
        val longEnough = abs(dxPx) >= distanceMinPx
        val fastEnough = abs(velocityXPxPerSec) >= flingPxPerSec &&
            (velocityXPxPerSec < 0f) == leftward
        return when {
            !longEnough && !fastEnough -> 0
            leftward -> 1
            else -> -1
        }
    }
}
