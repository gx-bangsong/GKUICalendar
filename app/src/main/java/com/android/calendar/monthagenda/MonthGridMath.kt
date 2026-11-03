/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Pure calendar arithmetic for the month grid. Months are 0-based here, matching
 * java.util.Calendar. [firstDayOfWeek] uses java.util.Calendar constants (1..7).
 * This file must not reference Android types.
 */
object MonthGridMath {

    @JvmStatic
    fun daysInMonth(year: Int, month: Int): Int =
        utcCalendar(year, month, 1).getActualMaximum(GregorianCalendar.DAY_OF_MONTH)

    /** Number of blank cells before day 1 of the month for the given week start. */
    @JvmStatic
    fun leadingBlanks(year: Int, month: Int, firstDayOfWeek: Int): Int {
        val dayOfWeek = utcCalendar(year, month, 1).get(GregorianCalendar.DAY_OF_WEEK)
        return (dayOfWeek - firstDayOfWeek + 7) % 7
    }

    /** Number of week rows (4, 5 or 6) needed to show the whole month. */
    @JvmStatic
    fun rowCount(year: Int, month: Int, firstDayOfWeek: Int): Int =
        (leadingBlanks(year, month, firstDayOfWeek) + daysInMonth(year, month) + 6) / 7

    /** Julian day number for a proleptic Gregorian date; month is 1-based. */
    @JvmStatic
    fun julianDay(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    /** Inverse of [julianDay]. Returns [year, month (1-based), day]. */
    @JvmStatic
    fun ymd(julianDay: Int): IntArray {
        val a = julianDay + 32044
        val b = (4 * a + 3) / 146097
        val c = a - 146097 * b / 4
        val d = (4 * c + 3) / 1461
        val e = c - 1461 * d / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = 100 * b + d - 4800 + m / 10
        return intArrayOf(year, month, day)
    }

    /**
     * The civil day [weeks] weeks away (negative for earlier). Julian day numbers are continuous,
     * so month, year and leap-day boundaries need no special case.
     */
    @JvmStatic
    fun shiftWeeks(julianDay: Int, weeks: Int): Int = julianDay + 7 * weeks

    /** First day of the week that contains [julianDay]; [firstDayOfWeek] uses Calendar constants (1..7). */
    @JvmStatic
    fun weekStart(julianDay: Int, firstDayOfWeek: Int): Int {
        val ymd = ymd(julianDay)
        val dayOfWeek = utcCalendar(ymd[0], ymd[1] - 1, ymd[2]).get(GregorianCalendar.DAY_OF_WEEK)
        return julianDay - (dayOfWeek - firstDayOfWeek + 7) % 7
    }

    private fun utcCalendar(year: Int, month: Int, day: Int): GregorianCalendar =
        GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }
}
