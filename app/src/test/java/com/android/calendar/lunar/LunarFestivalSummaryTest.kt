/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Test

class LunarFestivalSummaryTest {

    private val values = listOf("spring", "yuanxiao", "qingming", "duanwu")
    private val labels = listOf("Spring", "Lantern", "Qingming", "Dragon")
    private val all = "ALL"
    private val none = "NONE"
    private val sep = ", "

    private fun summarize(selected: Set<String>) =
        LunarFestivalSummary.summarize(selected, values, labels, all, none, sep)

    @Test
    fun noneSelectedShowsTheNoneText() {
        assertEquals(none, summarize(emptySet()))
    }

    @Test
    fun everyEntrySelectedShowsTheAllText() {
        assertEquals(all, summarize(values.toSet()))
    }

    @Test
    fun subsetFollowsTheEntryOrderNotTheStoredOrder() {
        // The stored set has no order. The summary still lists labels in entry order.
        assertEquals("Lantern, Dragon", summarize(setOf("duanwu", "yuanxiao")))
    }

    @Test
    fun singleSelectionIsItsLabelOnly() {
        assertEquals("Qingming", summarize(setOf("qingming")))
    }

    @Test
    fun unknownStoredValuesAreIgnored() {
        assertEquals("Spring", summarize(setOf("spring", "removed-entry")))
        assertEquals(none, summarize(setOf("removed-entry")))
    }

    @Test
    fun emptyEntryListShowsTheNoneText() {
        assertEquals(none, LunarFestivalSummary.summarize(
            setOf("spring"), emptyList(), emptyList(), all, none, sep))
    }

    @Test
    fun mismatchedLengthsUseTheShorterList() {
        // Only the first two entries have labels, so the third and fourth never show.
        assertEquals("Lantern", LunarFestivalSummary.summarize(
            setOf("yuanxiao", "qingming"), values, listOf("Spring", "Lantern"), all, none, sep))
    }
}
