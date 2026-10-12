/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

import com.android.calendar.home.DayMarkers.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayMarkersTest {

    private val first = MonthGridMath.julianDay(2026, 10, 1) // 2026-10-01

    @Test
    fun noSpans_meansNoMarkers() {
        val result = DayMarkers.build(first, 42, emptyList())
        assertEquals(emptyMap<Int, Int>(), result.counts)
        assertEquals(emptyMap<Int, List<Int>>(), result.colors)
    }

    @Test
    fun allDaySingleDay_countsOnlyThatDay() {
        val day = first + 4
        val result = DayMarkers.build(first, 42, listOf(Span(day, day, 0xFF0000)))
        assertEquals(mapOf(day to 1), result.counts)
        assertEquals(listOf(0xFF0000), result.colors[day])
    }

    @Test
    fun multiDaySpan_countsEachCoveredDay() {
        // Three-day trip from the 10th to the 12th.
        val start = first + 9
        val result = DayMarkers.build(first, 42, listOf(Span(start, start + 2, 1)))
        assertEquals(setOf(start, start + 1, start + 2), result.counts.keys)
        assertEquals(1, result.counts[start + 1])
    }

    @Test
    fun spanCrossingGridEdges_isClippedToTheGrid() {
        // Starts before the grid and ends after it.
        val spans = listOf(Span(first - 3, first + 2, 1), Span(first + 40, first + 50, 2))
        val result = DayMarkers.build(first, 42, spans)
        assertEquals(setOf(first, first + 1, first + 2, first + 40, first + 41),
            result.counts.keys)
        assertNull(result.counts[first - 1])
        assertNull(result.counts[first + 42])
    }

    @Test
    fun recurringInstances_eachOccurrenceIsCountedOnItsOwnDay() {
        // A daily recurring event produces one instance per day.
        val spans = (0 until 7).map { Span(first + it, first + it, 0x00FF00) }
        val result = DayMarkers.build(first, 42, spans)
        for (i in 0 until 7) {
            assertEquals(1, result.counts[first + i])
        }
        assertNull(result.counts[first + 7])
    }

    @Test
    fun recurringExceptionMovedAway_doesNotLeaveAMarkerOnTheOriginalDay() {
        // The series has an instance on day 5, but the exception moved it to day 6.
        // The Instances provider returns only the moved instance, so day 5 stays empty.
        val moved = listOf(Span(first + 6, first + 6, 1))
        val result = DayMarkers.build(first, 42, moved)
        assertNull(result.counts[first + 5])
        assertEquals(1, result.counts[first + 6])
    }

    @Test
    fun countIsPerInstance_notPerEventId() {
        // Two instances of different events on the same day give a count of two.
        val spans = listOf(Span(first + 2, first + 2, 1), Span(first + 2, first + 2, 2))
        val result = DayMarkers.build(first, 42, spans)
        assertEquals(2, result.counts[first + 2])
    }

    @Test
    fun colorDotsAreCappedAndDeduplicated() {
        val spans = listOf(
            Span(first, first, 10),
            Span(first, first, 10),
            Span(first, first, 20),
            Span(first, first, 30),
            Span(first, first, 40)
        )
        val result = DayMarkers.build(first, 42, spans)
        assertEquals(listOf(10, 20, 30), result.colors[first])
        assertEquals(5, result.counts[first])
    }
}
