/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import com.android.calendar.month.CellSlotMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cell row height is computed by CellSlotMath, the same object the view uses for its slots.
 * These tests check the formulas (pure math), not a rendered layout. Rendering is verified on device.
 */
class DayCellMeasureTest {

    @Test
    fun rowHeightAtDefaultFontAndDensityMatchesTheSlots() {
        // density 2, font 1.0: padding 12 + day slot 56 (28 dp) + dots 14 = 82 px. No lunar slot.
        assertEquals(82, CellSlotMath.minRowHeightPx(2f, 1f, lunarEnabled = false))
        // Lunar adds one line of 11 sp: 11 * 1.3 * 2 = 28.6, rounded up to 29 px.
        assertEquals(82 + 29, CellSlotMath.minRowHeightPx(2f, 1f, lunarEnabled = true))
    }

    @Test
    fun rowHeightGrowsWithFontScaleAndNeverShrinks() {
        for (density in listOf(1f, 1.5f, 2f, 2.625f, 3f)) {
            var previous = 0
            for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.6f, 2f)) {
                val plain = CellSlotMath.minRowHeightPx(density, scale, lunarEnabled = false)
                val lunar = CellSlotMath.minRowHeightPx(density, scale, lunarEnabled = true)
                assertTrue("lunar row must not be shorter", lunar >= plain)
                assertTrue("row must grow with font scale", plain >= previous)
                previous = plain
            }
        }
    }

    @Test
    fun daySlotKeepsItsOwnSpaceAtLargeFont() {
        // The day number never gets less than its own line at this font scale.
        for (scale in listOf(1f, 1.3f, 2f)) {
            val density = 2.625f
            val line = 14f * scale * density * 1.3f
            assertTrue(CellSlotMath.daySlotPx(density, scale) >= Math.ceil(line.toDouble()))
        }
        // At normal size the circle is still at least 28 dp.
        assertTrue(CellSlotMath.daySlotPx(2.625f, 1f) >= Math.ceil(28 * 2.625))
    }

    @Test
    fun lunarSlotIsOwnLine() {
        // density 2, font 2.0: 11 * 2 * 2 * 1.3 = 57.2, rounded up to 58 px.
        assertEquals(58, CellSlotMath.lunarSlotPx(2f, 2f))
    }

    @Test
    fun largeFontRowIsTallerThanNormalButBoundedByTheLunarToggle() {
        val normal = CellSlotMath.minRowHeightPx(2f, 1f, lunarEnabled = true)
        val large = CellSlotMath.minRowHeightPx(2f, 2f, lunarEnabled = true)
        assertTrue(large > normal)
        // Hiding lunar at large font saves exactly one lunar slot.
        val largeNoLunar = CellSlotMath.minRowHeightPx(2f, 2f, lunarEnabled = false)
        assertEquals(CellSlotMath.lunarSlotPx(2f, 2f), large - largeNoLunar)
    }
}
