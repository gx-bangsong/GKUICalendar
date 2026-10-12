/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Week steps for the collapsed month-agenda view. The rule is civil-day arithmetic: one step is
 * seven days on the same weekday, across month, year and leap-day boundaries, for every week start.
 */
class WeekNavigationTest {

    private fun jd(year: Int, month: Int, day: Int): Int = MonthGridMath.julianDay(year, month, day)

    private fun weekdayOf(julian: Int): Int {
        val ymd = MonthGridMath.ymd(julian)
        return GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(ymd[0], ymd[1] - 1, ymd[2])
        }.get(GregorianCalendar.DAY_OF_WEEK)
    }

    @Test
    fun stepCrossesTheNewYear() {
        assertEquals(jd(2026, 1, 7), MonthGridMath.shiftWeeks(jd(2025, 12, 31), 1))
        assertEquals(jd(2025, 12, 27), MonthGridMath.shiftWeeks(jd(2026, 1, 3), -1))
    }

    @Test
    fun stepCrossesALeapDay() {
        assertEquals(jd(2028, 3, 6), MonthGridMath.shiftWeeks(jd(2028, 2, 28), 1))
        assertEquals(jd(2028, 2, 29), MonthGridMath.shiftWeeks(jd(2028, 3, 7), -1))
    }

    @Test
    fun stepKeepsTheWeekday() {
        val start = jd(2024, 1, 1)
        for (weeks in -3..3) {
            assertEquals(weekdayOf(start), weekdayOf(MonthGridMath.shiftWeeks(start, weeks)))
        }
    }

    @Test
    fun weekStartDependsOnTheWeekStartDay() {
        // 2026-10-12 is a Monday.
        val monday = jd(2026, 10, 12)
        assertEquals(monday, MonthGridMath.weekStart(monday, GregorianCalendar.MONDAY))
        assertEquals(monday - 1, MonthGridMath.weekStart(monday, GregorianCalendar.SUNDAY))
        assertEquals(monday - 6, MonthGridMath.weekStart(monday, GregorianCalendar.TUESDAY))
    }

    @Test
    fun everyDayOfEveryWeekStartIsInItsWeek_andStepsByExactlySevenDays() {
        for (firstDay in GregorianCalendar.SUNDAY..GregorianCalendar.SATURDAY) {
            for (day in jd(2024, 1, 1)..jd(2026, 12, 31)) {
                val start = MonthGridMath.weekStart(day, firstDay)
                assertTrue(day - start in 0..6)
                assertEquals(firstDay, weekdayOf(start))
                assertEquals(
                    start + 7,
                    MonthGridMath.weekStart(MonthGridMath.shiftWeeks(day, 1), firstDay)
                )
            }
        }
    }

    @Test
    fun collapsedRowIsTheSelectedWeek() {
        // The collapsed grid shows row = selectedRow(...). That row must be the selected week.
        for (firstDay in GregorianCalendar.SUNDAY..GregorianCalendar.SATURDAY) {
            for (month in 0 until 12) {
                val year = 2026
                val rows = MonthGridMath.rowCount(year, month, firstDay)
                val firstCell = jd(year, month + 1, 1) -
                    MonthGridMath.leadingBlanks(year, month, firstDay)
                for (day in 1..MonthGridMath.daysInMonth(year, month)) {
                    val julian = jd(year, month + 1, day)
                    val row = PanelMath.selectedRow(julian, firstCell, rows)
                    assertEquals(
                        MonthGridMath.weekStart(julian, firstDay),
                        firstCell + row * 7
                    )
                }
            }
        }
    }
}
