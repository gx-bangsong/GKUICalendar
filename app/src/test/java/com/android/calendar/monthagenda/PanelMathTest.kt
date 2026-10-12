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
    fun userChoiceIsKeptButAutoDowngradeWins() {
        // An explicit "expanded" survives a small window, but the downgrade is applied for as
        // long as the window is small. An explicit "collapsed" always holds.
        assertFalse(PanelMath.isExpanded(true, autoCollapsed = true))
        assertTrue(PanelMath.isExpanded(true, autoCollapsed = false))
        assertFalse(PanelMath.isExpanded(false, autoCollapsed = false))
    }

    @Test
    fun autoRuleAppliesWithoutUserChoice() {
        assertFalse(PanelMath.isExpanded(null, autoCollapsed = true))
        assertTrue(PanelMath.isExpanded(null, autoCollapsed = false))
    }

    @Test
    fun autoCollapseIsRecomputedOnResizeAndNeverReused() {
        // Chrome 124, list minimum 120, six rows: the expanded grid needs 6 * row + 244 px.
        val chrome = 124
        val list = 120
        val lunarRow = 56
        val plainRow = 50
        val tall = PanelMath.planLayout(800, chrome, list, 6, lunarRow, plainRow, lunarWanted = true)
        assertEquals(PanelMath.LayoutPlan(lunarShown = true, autoCollapsed = false), tall)

        val medium = PanelMath.planLayout(560, chrome, list, 6, lunarRow, plainRow, lunarWanted = true)
        assertEquals(PanelMath.LayoutPlan(lunarShown = false, autoCollapsed = false), medium)

        val short = PanelMath.planLayout(400, chrome, list, 6, lunarRow, plainRow, lunarWanted = true)
        assertTrue(short.autoCollapsed)
        assertFalse(PanelMath.isExpanded(null, short.autoCollapsed))

        // The window grows again: the same state, recomputed, expands. The old downgrade is not reused.
        val back = PanelMath.planLayout(800, chrome, list, 6, lunarRow, plainRow, lunarWanted = true)
        assertTrue(PanelMath.isExpanded(null, back.autoCollapsed))
    }

    @Test
    fun expandedGridNeverSqueezesTheListBelowItsMinimum() {
        // Property over body heights: whenever the grid is not auto-collapsed, six rows at the
        // chosen row height plus the chrome leave at least the list minimum.
        val chrome = 124
        val list = 120
        for (body in 200..1200 step 5) {
            for (lunarWanted in listOf(true, false)) {
                val plan = PanelMath.planLayout(body, chrome, list, 6, 79, 50, lunarWanted)
                val rowUsed = if (plan.lunarShown) 79 else 50
                if (!plan.autoCollapsed) {
                    assertTrue("body=$body", 6 * rowUsed + chrome + list <= body)
                }
            }
        }
    }

    @Test
    fun largeFontHidesLunarBeforeCollapsing() {
        // Lunar row too tall for the window, plain row fits: lunar goes, the grid stays expanded.
        val plan = PanelMath.planLayout(640, 124, 120, 6, lunarRow = 90, plainRow = 50, lunarWanted = true)
        assertEquals(PanelMath.LayoutPlan(lunarShown = false, autoCollapsed = false), plan)
    }

    @Test
    fun maxProgressBlocksExpansionWhileAutoCollapsed() {
        assertEquals(0f, PanelMath.maxProgress(autoCollapsed = true), eps)
        assertEquals(1f, PanelMath.maxProgress(autoCollapsed = false), eps)
    }

    @Test
    fun rawCoordinateIsTheScreenPositionOfEachPointer() {
        // Primary pointer: local 10 at raw 110, so the view sits 100 px from the screen origin.
        assertEquals(110f, PanelMath.rawCoordinate(10f, rawPrimary = 110f, localPrimary = 10f), eps)
        // A second pointer in the same event shares that offset.
        assertEquals(150f, PanelMath.rawCoordinate(50f, rawPrimary = 110f, localPrimary = 10f), eps)
        // The view has moved, so local values change. The raw value of the same finger does not.
        assertEquals(130f, PanelMath.rawCoordinate(-10f, rawPrimary = 110f, localPrimary = -30f), eps)
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
