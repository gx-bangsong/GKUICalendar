/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.traffic.data

/**
 * Pure-JVM engine for license-plate driving restrictions (限行).
 *
 * Two rule modes are supported:
 *
 *  * [MODE_TAIL_NUMBER] — the Beijing-style scheme where Monday..Friday each
 *    restrict two plate tail digits. The five digit pairs rotate as a block
 *    every quarter, so the active rotation is expressed as a *group offset*
 *    (0..4) that the user can correct from the settings screen when the
 *    traffic bureau publishes a new period.
 *  * [MODE_ODD_EVEN] — 单双号: odd tails are restricted on odd days of the
 *    month, even tails on even days (weekends excluded like above).
 *
 * Weekends are never restricted. Statutory-holiday compensation is out of
 * scope for this phase (the calendar has no holiday table yet), which is
 * documented to the user in the settings screen.
 */
object TrafficRules {

    const val MODE_TAIL_NUMBER = 0
    const val MODE_ODD_EVEN = 1

    /** Monday..Friday digit pairs for group offset 0. */
    @JvmField
    val PAIRS: Array<IntArray> = arrayOf(
        intArrayOf(1, 6),
        intArrayOf(2, 7),
        intArrayOf(3, 8),
        intArrayOf(4, 9),
        intArrayOf(5, 0)
    )

    /**
     * Known Beijing rotation periods: `[startJulianDay, mondayGroupIndex]`,
     * ascending. Used only to pick a sensible *default* offset; the user can
     * always override it, because the bureau republishes the table quarterly.
     */
    private val KNOWN_PERIODS: Array<IntArray> = arrayOf(
        intArrayOf(julianDayOf(2025, 9, 29), 3),
        intArrayOf(julianDayOf(2025, 12, 29), 4),
        intArrayOf(julianDayOf(2026, 3, 30), 1),
        intArrayOf(julianDayOf(2026, 6, 29), 0)
    )

    /** Default rotation group for [julianDay], from the known-period table. */
    @JvmStatic
    fun defaultGroupOffset(julianDay: Int): Int {
        var offset = KNOWN_PERIODS[0][1]
        for (p in KNOWN_PERIODS) {
            if (julianDay >= p[0]) offset = p[1]
        }
        return offset
    }

    /** 0 = Sunday .. 6 = Saturday, for an Android julian day. */
    @JvmStatic
    fun weekDayOf(julianDay: Int): Int = ((julianDay + 1) % 7 + 7) % 7

    @JvmStatic
    fun isWeekend(julianDay: Int): Boolean {
        val wd = weekDayOf(julianDay)
        return wd == 0 || wd == 6
    }

    /**
     * @return the two restricted tail digits on [julianDay] for the
     *         tail-number mode, or null on weekends.
     */
    @JvmStatic
    fun restrictedDigits(julianDay: Int, groupOffset: Int): IntArray? {
        if (isWeekend(julianDay)) return null
        val weekdayIndex = weekDayOf(julianDay) - 1 // Mon = 0 .. Fri = 4
        val idx = ((weekdayIndex + groupOffset) % PAIRS.size + PAIRS.size) % PAIRS.size
        return PAIRS[idx]
    }

    /** @return true when a plate ending in [tailDigit] may not drive that day. */
    @JvmStatic
    fun isRestricted(
        mode: Int,
        tailDigit: Int,
        groupOffset: Int,
        julianDay: Int,
        dayOfMonth: Int
    ): Boolean {
        if (isWeekend(julianDay)) return false
        return when (mode) {
            MODE_ODD_EVEN -> (tailDigit % 2) == (dayOfMonth % 2)
            else -> {
                val digits = restrictedDigits(julianDay, groupOffset) ?: return false
                digits[0] == tailDigit || digits[1] == tailDigit
            }
        }
    }

    /** Proleptic-Gregorian → Android julian day (same epoch as Time.getJulianDay). */
    @JvmStatic
    fun julianDayOf(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }
}
