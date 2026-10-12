/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelMathTest {

    private val eps = 0.0001f

    @Test
    fun dragIsClampedToTheRange() {
        assertEquals(0f, PanelMath.progressFromDrag(0.2f, -1000f, 400f), eps)
        assertEquals(1f, PanelMath.progressFromDrag(0.8f, 1000f, 400f), eps)
        assertEquals(0.5f, PanelMath.progressFromDrag(0f, 200f, 400f), eps)
        assertEquals(0.3f, PanelMath.progressFromDrag(0.3f, 10f, 0f), eps)
    }

    @Test
    fun releaseSnapsByPositionWhenSlow() {
        assertEquals(1f, PanelMath.snapTarget(0.6f, 0f, 800f), eps)
        assertEquals(0f, PanelMath.snapTarget(0.4f, 0f, 800f), eps)
        assertEquals(1f, PanelMath.snapTarget(0.5f, 0f, 800f), eps)
    }

    @Test
    fun fastFlingWinsOverPosition() {
        // Up (negative) collapses even from a mostly expanded position.
        assertEquals(0f, PanelMath.snapTarget(0.9f, -1200f, 800f), eps)
        // Down (positive) expands even from a mostly collapsed position.
        assertEquals(1f, PanelMath.snapTarget(0.1f, 1200f, 800f), eps)
        // Slow motion does not override position.
        assertEquals(0f, PanelMath.snapTarget(0.1f, -300f, 800f), eps)
    }

    @Test
    fun userChoiceOverridesAutoRule() {
        assertTrue(PanelMath.isExpanded(true, autoCollapsed = true))
        assertFalse(PanelMath.isExpanded(false, autoCollapsed = false))
    }

    @Test
    fun autoRuleAppliesWithoutUserChoice() {
        assertFalse(PanelMath.isExpanded(null, autoCollapsed = true))
        assertTrue(PanelMath.isExpanded(null, autoCollapsed = false))
    }

    @Test
    fun autoRuleIsRecomputedFromTheCurrentHeight() {
        // An expired auto-collapse must not stick: the same user-less state becomes expanded
        // again once the window is tall enough.
        val threshold = 560
        assertTrue(PanelMath.isAutoCollapsed(400, threshold))
        assertFalse(PanelMath.isAutoCollapsed(700, threshold))
        assertFalse(PanelMath.isExpanded(null, PanelMath.isAutoCollapsed(700, threshold)))
    }

    @Test
    fun gridHeightGoesFromOneRowToAllRows() {
        assertEquals(50, PanelMath.gridHeightPx(0f, 50, 6))
        assertEquals(300, PanelMath.gridHeightPx(1f, 50, 6))
        assertEquals(175, PanelMath.gridHeightPx(0.5f, 50, 6))
        assertEquals(50, PanelMath.gridHeightPx(1f, 50, 1))
    }

    @Test
    fun collapsedGridKeepsTheSelectedWeekVisible() {
        assertEquals(0f, PanelMath.gridTranslationPx(1f, 3, 50), eps)
        assertEquals(-150f, PanelMath.gridTranslationPx(0f, 3, 50), eps)
    }

    @Test
    fun selectedRowIsWithinTheGrid() {
        assertEquals(0, PanelMath.selectedRow(100, 100, 5))
        assertEquals(2, PanelMath.selectedRow(100 + 15, 100, 5))
        assertEquals(4, PanelMath.selectedRow(100 + 35, 100, 5))
        assertEquals(4, PanelMath.selectedRow(100 + 99, 100, 5))
    }

    @Test
    fun swipeNeedsHorizontalDominance() {
        assertTrue(PanelMath.isHorizontalSwipe(40f, 5f, 16f))
        assertFalse(PanelMath.isHorizontalSwipe(40f, 30f, 16f))
        assertFalse(PanelMath.isHorizontalSwipe(10f, 0f, 16f))
    }

    @Test
    fun swipeDistanceOrFlingFlipsTheMonth() {
        // Leftward is the next month, rightward the previous one.
        assertEquals(1, PanelMath.swipeMonthDelta(-120f, 0f, 64f, 800f))
        assertEquals(-1, PanelMath.swipeMonthDelta(120f, 0f, 64f, 800f))
        // A short but fast flick also counts, in its own direction only.
        assertEquals(1, PanelMath.swipeMonthDelta(-30f, -1500f, 64f, 800f))
        assertEquals(0, PanelMath.swipeMonthDelta(-30f, 1500f, 64f, 800f))
        // A short slow drag does nothing.
        assertEquals(0, PanelMath.swipeMonthDelta(-30f, -100f, 64f, 800f))
    }
}
