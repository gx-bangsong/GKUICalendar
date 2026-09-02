/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.shift.data

/**
 * Xiaomi-style built-in shift presets. Each preset is a repeating IntArray
 * of [ShiftType] codes. Phase 1b exposes three quick presets.
 */
object ShiftPresets {

    const val KEY_THREE       = "three"
    const val KEY_FOUR_THREE  = "four_three"
    const val KEY_TWO_ON_TWO_OFF = "two_on_two_off"

    @JvmStatic
    fun shortLabel(code: Int): String = when (code) {
        ShiftType.MORNING   -> "\u65e9"
        ShiftType.AFTERNOON -> "\u4e2d"
        ShiftType.NIGHT     -> "\u665a"
        ShiftType.REST      -> "\u4f11"
        else -> "?"
    }

    @JvmStatic
    fun cycleForKey(key: String?): IntArray? = when (key) {
        KEY_THREE -> intArrayOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT)
        KEY_FOUR_THREE -> intArrayOf(
            ShiftType.MORNING, ShiftType.MORNING,
            ShiftType.AFTERNOON, ShiftType.AFTERNOON,
            ShiftType.NIGHT, ShiftType.NIGHT,
            ShiftType.REST, ShiftType.REST
        )
        KEY_TWO_ON_TWO_OFF -> intArrayOf(
            ShiftType.MORNING, ShiftType.MORNING,
            ShiftType.REST, ShiftType.REST
        )
        else -> null
    }

    @JvmStatic
    fun displayName(key: String?): String = when (key) {
        KEY_THREE       -> "\u4e09\u73ed\u5012"
        KEY_FOUR_THREE  -> "\u56db\u73ed\u4e09\u5012"
        KEY_TWO_ON_TWO_OFF -> "\u4e0a\u4e8c\u4f11\u4e8c"
        else -> ""
    }
}
