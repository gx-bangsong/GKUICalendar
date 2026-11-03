/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import com.android.calendar.lunar.LunarHelper
import com.android.calendar.lunar.LunarMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The month agenda must follow the same lunar modes as the classic month view.
 * July 2024 is used as the window-free case: the 端午 window closes on 06-13 and the 七夕
 * window opens on 08-05, and solar terms are off in these tests.
 */
class MonthLunarLabelsTest {

    private val all = LunarHelper.DEFAULT_FESTIVALS
    private val today = jd(2024, 7, 1)

    private fun jd(year: Int, month: Int, day: Int): Int =
        LunarHelper.ymdToJulianDay(year, month, day)

    private fun july(mode: LunarMode): Map<Int, MonthLunarLabels.Label> =
        MonthLunarLabels.build(jd(2024, 7, 1), 31, mode, all, false, false, today)

    @Test
    fun offShowsNoLunarText() {
        assertTrue(july(LunarMode.OFF).isEmpty())
    }

    @Test
    fun contextualHidesEveryDayOutsideAWindow() {
        assertTrue(july(LunarMode.CONTEXTUAL).isEmpty())
    }

    @Test
    fun alwaysShowsEveryDayIncludingOutsideWindows() {
        val labels = july(LunarMode.ALWAYS)
        assertEquals(31, labels.size)
        val outside = labels.getValue(jd(2024, 7, 15))
        assertTrue(outside.text.isNotEmpty())
        assertFalse(outside.emphasized)
    }

    @Test
    fun contextualShowsWindowDaysAndEmphasizesTheFestivalDay() {
        val june = MonthLunarLabels.build(
            jd(2024, 6, 1), 30, LunarMode.CONTEXTUAL, all, false, false, jd(2024, 6, 1)
        )
        // 端午 is 2024-06-10. Its window is 06-05 .. 06-13.
        val festival = june.getValue(jd(2024, 6, 10))
        assertTrue(festival.emphasized)
        assertEquals("端午", festival.text)
        // Inside the window but not the festival day: visible, not emphasized.
        assertFalse(june.getValue(jd(2024, 6, 8)).emphasized)
        // One day after the window closes: hidden.
        assertNull(june[jd(2024, 6, 14)])
    }

    @Test
    fun contextualOutsideTheWindowMatchesOff_andAlwaysDiffers() {
        val day = jd(2024, 7, 15)
        assertNull(july(LunarMode.CONTEXTUAL)[day])
        assertNull(july(LunarMode.OFF)[day])
        assertNotNull(july(LunarMode.ALWAYS)[day])
    }

    @Test
    fun bothEnabledModesReserveTheSlotOnlyOffDoesNot() {
        assertFalse(LunarHelper.needsLunarSlot(LunarMode.OFF))
        assertTrue(LunarHelper.needsLunarSlot(LunarMode.CONTEXTUAL))
        assertTrue(LunarHelper.needsLunarSlot(LunarMode.ALWAYS))
    }
}
