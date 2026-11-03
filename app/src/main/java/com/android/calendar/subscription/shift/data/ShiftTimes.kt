/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.shift.data

import android.content.Context

/**
 * Per-shift-type clock times, restoring the customisable durations the
 * pre-refactor `ShiftPreset` had (`startTime` / `endTime` / `alarmOffset`,
 * all in minutes from midnight).
 *
 * Phase 1b hard-coded 07:30 / 15:30 / 23:30 as display strings. They are now
 * real, editable values so a 12-hour rotation (e.g. 08:00-20:00) or any other
 * duration works. Night shifts legitimately wrap past midnight, so an end
 * time that is <= the start time means "ends the next day".
 */
object ShiftTimes {

    private const val PREFS = "subscription_shift"
    private const val KEY_START = "shift_start_"
    private const val KEY_END = "shift_end_"
    private const val KEY_ALARM = "shift_alarm_"

    /** Alarm disabled sentinel. */
    const val NO_ALARM = -1

    private fun defaultStart(type: Int): Int = when (type) {
        ShiftType.MORNING -> 7 * 60 + 30
        ShiftType.AFTERNOON -> 15 * 60 + 30
        ShiftType.NIGHT -> 23 * 60 + 30
        else -> 0
    }

    private fun defaultEnd(type: Int): Int = when (type) {
        ShiftType.MORNING -> 15 * 60 + 30
        ShiftType.AFTERNOON -> 23 * 60 + 30
        ShiftType.NIGHT -> 7 * 60 + 30
        else -> 0
    }

    @JvmStatic
    fun getStart(ctx: Context, type: Int): Int =
        prefs(ctx).getInt(KEY_START + type, defaultStart(type))

    @JvmStatic
    fun getEnd(ctx: Context, type: Int): Int =
        prefs(ctx).getInt(KEY_END + type, defaultEnd(type))

    /** Minutes before the start time to fire a reminder, or [NO_ALARM]. */
    @JvmStatic
    fun getAlarmOffset(ctx: Context, type: Int): Int =
        prefs(ctx).getInt(KEY_ALARM + type, NO_ALARM)

    @JvmStatic
    fun setTimes(ctx: Context, type: Int, startMinutes: Int, endMinutes: Int) {
        prefs(ctx).edit()
            .putInt(KEY_START + type, wrap(startMinutes))
            .putInt(KEY_END + type, wrap(endMinutes))
            .apply()
    }

    @JvmStatic
    fun setAlarmOffset(ctx: Context, type: Int, minutesBefore: Int) {
        prefs(ctx).edit().putInt(KEY_ALARM + type, minutesBefore).apply()
    }

    /** Shift length in minutes, treating end <= start as crossing midnight. */
    @JvmStatic
    fun durationMinutes(startMinutes: Int, endMinutes: Int): Int {
        val raw = endMinutes - startMinutes
        return if (raw > 0) raw else raw + 24 * 60
    }

    @JvmStatic
    fun durationMinutes(ctx: Context, type: Int): Int =
        durationMinutes(getStart(ctx, type), getEnd(ctx, type))

    /** "07:30" */
    @JvmStatic
    fun formatTime(minutes: Int): String {
        val m = wrap(minutes)
        val h = m / 60
        val mm = m % 60
        return (if (h < 10) "0" else "") + h + ":" + (if (mm < 10) "0" else "") + mm
    }

    /** "07:30-15:30" */
    @JvmStatic
    fun formatRange(startMinutes: Int, endMinutes: Int): String =
        formatTime(startMinutes) + "-" + formatTime(endMinutes)

    private fun wrap(minutes: Int): Int = ((minutes % 1440) + 1440) % 1440

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
