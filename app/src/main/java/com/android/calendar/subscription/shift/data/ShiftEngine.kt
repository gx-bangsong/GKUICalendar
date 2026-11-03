/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.shift.data

/**
 * Pure-JVM helpers for computing the shift on a given Julian day.
 *
 * The cycle is an IntArray of [ShiftType] codes anchored at [startJulianDay].
 * Days before the anchor return -1 (pattern hasn't begun).
 */
object ShiftEngine {

    /** @return ShiftType code for [jd], or -1 if not covered. */
    @JvmStatic
    fun typeFor(cycle: IntArray, startJulianDay: Int, jd: Int): Int {
        if (cycle.isEmpty()) return -1
        val diff = jd - startJulianDay
        if (diff < 0) return -1
        return cycle[diff.mod(cycle.size)]
    }

    @JvmStatic
    fun labelFor(typeCode: Int): String = ShiftPresets.shortLabel(typeCode)
}
