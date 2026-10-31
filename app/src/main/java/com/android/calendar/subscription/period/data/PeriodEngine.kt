/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.period.data

/**
 * Pure-JVM cycle math for the 生理期 (period) subscription.
 *
 * The model is the classic calendar method: a cycle of [cycleLength] days
 * anchored on the first day of the user's last recorded period, with bleeding
 * lasting [periodLength] days. Ovulation is estimated at 14 days before the
 * next period starts, and the fertile window spans the five days before
 * ovulation through the day after it.
 *
 * Days before the anchor return [NONE] — the app never guesses backwards.
 */
object PeriodEngine {

    const val NONE = 0
    /** Bleeding day. */
    const val PERIOD = 1
    /** Estimated ovulation day. */
    const val OVULATION = 2
    /** Fertile window (excluding the ovulation day itself). */
    const val FERTILE = 3
    /** Day the next period is predicted to start. */
    const val PREDICTED_START = 4

    const val DEFAULT_CYCLE_LENGTH = 28
    const val DEFAULT_PERIOD_LENGTH = 5
    const val MIN_CYCLE_LENGTH = 20
    const val MAX_CYCLE_LENGTH = 45
    const val MIN_PERIOD_LENGTH = 2
    const val MAX_PERIOD_LENGTH = 10

    /**
     * @param anchorJd     first day of the last recorded period
     * @return the day index within the current cycle (0-based), or -1 when
     *         [jd] is before the anchor.
     */
    @JvmStatic
    fun cycleDayIndex(anchorJd: Int, cycleLength: Int, jd: Int): Int {
        if (cycleLength <= 0) return -1
        val diff = jd - anchorJd
        if (diff < 0) return -1
        return diff % cycleLength
    }

    /** @return one of [NONE], [PERIOD], [OVULATION], [FERTILE], [PREDICTED_START]. */
    @JvmStatic
    fun phaseFor(anchorJd: Int, cycleLength: Int, periodLength: Int, jd: Int): Int {
        val idx = cycleDayIndex(anchorJd, cycleLength, jd)
        if (idx < 0) return NONE
        if (idx < periodLength) {
            // Day 0 of a *future* cycle is a prediction rather than a record.
            return if (idx == 0 && jd > anchorJd) PREDICTED_START else PERIOD
        }
        val ovulation = cycleLength - 14
        if (ovulation <= periodLength) return NONE
        if (idx == ovulation) return OVULATION
        if (idx >= ovulation - 5 && idx <= ovulation + 1) return FERTILE
        return NONE
    }

    /** @return julian day of the next predicted period start on/after [fromJd]. */
    @JvmStatic
    fun nextPeriodStart(anchorJd: Int, cycleLength: Int, fromJd: Int): Int {
        if (cycleLength <= 0) return anchorJd
        var start = anchorJd
        while (start < fromJd) start += cycleLength
        return start
    }

    /** @return days remaining until the next predicted start (0 = today). */
    @JvmStatic
    fun daysUntilNextStart(anchorJd: Int, cycleLength: Int, todayJd: Int): Int =
        nextPeriodStart(anchorJd, cycleLength, todayJd) - todayJd

    @JvmStatic
    fun clampCycleLength(v: Int): Int = v.coerceIn(MIN_CYCLE_LENGTH, MAX_CYCLE_LENGTH)

    @JvmStatic
    fun clampPeriodLength(v: Int): Int = v.coerceIn(MIN_PERIOD_LENGTH, MAX_PERIOD_LENGTH)
}
