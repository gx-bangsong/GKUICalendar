/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthGridMathTest {

    @Test
    fun february2026_startingSunday_needsFourRows() {
        // 2026-02-01 is a Sunday and the month has 28 days.
        assertEquals(28, MonthGridMath.daysInMonth(2026, 1))
        assertEquals(0, MonthGridMath.leadingBlanks(2026, 1, Calendar.SUNDAY))
        assertEquals(4, MonthGridMath.rowCount(2026, 1, Calendar.SUNDAY))
    }

    @Test
    fun february2026_startingMonday_needsFiveRows() {
        // With Monday first, 2026-02-01 (Sunday) sits after six blank cells: (6 + 28 + 6) / 7 = 5 rows.
        assertEquals(6, MonthGridMath.leadingBlanks(2026, 1, Calendar.MONDAY))
        assertEquals(5, MonthGridMath.rowCount(2026, 1, Calendar.MONDAY))
    }

    @Test
    fun leapFebruary2028_startingTuesday_needsFiveRows() {
        assertEquals(29, MonthGridMath.daysInMonth(2028, 1))
        assertEquals(2, MonthGridMath.leadingBlanks(2028, 1, Calendar.SUNDAY))
        assertEquals(5, MonthGridMath.rowCount(2028, 1, Calendar.SUNDAY))
    }

    @Test
    fun leapFebruary2024_startingThursday_fiveRowsWithSundayStart() {
        assertEquals(29, MonthGridMath.daysInMonth(2024, 1))
        assertEquals(5, MonthGridMath.rowCount(2024, 1, Calendar.SUNDAY))
    }

    @Test
    fun sixRowMonth_isDetected() {
        // 2026-08-01 is a Saturday; with Sunday start that is 6 leading blanks + 31 days.
        assertEquals(6, MonthGridMath.leadingBlanks(2026, 7, Calendar.SUNDAY))
        assertEquals(6, MonthGridMath.rowCount(2026, 7, Calendar.SUNDAY))
    }

    @Test
    fun everyMonthOfEveryWeekStartGivesFourToSixRows_andCoversTheMonth() {
        for (year in 2024..2032) {
            for (month in 0..11) {
                for (firstDow in Calendar.SUNDAY..Calendar.SATURDAY) {
                    val rows = MonthGridMath.rowCount(year, month, firstDow)
                    assertTrue("rows=$rows for $year-$month dow=$firstDow", rows in 4..6)
                    val cells = rows * 7
                    val used = MonthGridMath.leadingBlanks(year, month, firstDow) +
                        MonthGridMath.daysInMonth(year, month)
                    assertTrue("month does not fit $year-$month dow=$firstDow", used <= cells)
                    assertTrue("extra row for $year-$month dow=$firstDow", cells - used < 7)
                }
            }
        }
    }

    @Test
    fun julianDay_roundTripsThroughYmd_forSeveralYears() {
        val utc = TimeZone.getTimeZone("UTC")
        val cal = GregorianCalendar(utc)
        cal.clear()
        cal.set(1999, Calendar.DECEMBER, 25)
        repeat(365 * 8) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val jd = MonthGridMath.julianDay(y, m, d)
            val back = MonthGridMath.ymd(jd)
            assertEquals(y, back[0])
            assertEquals(m, back[1])
            assertEquals(d, back[2])
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    @Test
    fun julianDay_differenceMatchesCalendarDays() {
        // 2000-01-01 is JDN 2451545 in the standard convention.
        assertEquals(2451545, MonthGridMath.julianDay(2000, 1, 1))
        assertEquals(2451545 + 366, MonthGridMath.julianDay(2001, 1, 1))
    }
}
