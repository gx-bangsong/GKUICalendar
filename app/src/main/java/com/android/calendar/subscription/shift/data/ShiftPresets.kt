/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.shift.data

import androidx.annotation.ColorInt

/**
 * Xiaomi-MIUI/HyperOS quick presets for the built-in 轮班助手.
 *
 * The five preset buttons match Xiaomi's UI:
 *   三班倒       早→中→晚 (no rest; 3-day cycle)
 *   四班三倒     早早中中晚晚休休 (8-day 4-crew 3-shift rotation)
 *   上一休一     早休 (2-day cycle, single day shift)
 *   上二休二     早早休休 (4-day cycle, single day shift)
 *   常白班       早 (1-day cycle, fixed day shift)
 *
 * Cycles use the single [ShiftType] codes the badge renderer consumes.
 * Badge colors follow Xiaomi's palette: 早 green, 中 orange, 晚 indigo,
 * 休 onSurfaceVariant.
 */
object ShiftPresets {

    const val KEY_THREE          = "three"
    const val KEY_FOUR_THREE     = "four_three"
    const val KEY_ONE_ON_ONE_OFF = "one_on_one_off"
    const val KEY_TWO_ON_TWO_OFF = "two_on_two_off"
    const val KEY_DAY_SHIFT      = "day_shift"

    /** Ordered list of all preset keys (button order in the UI). */
    @JvmField
    val ORDERED_KEYS: List<String> = listOf(
        KEY_THREE, KEY_FOUR_THREE,
        KEY_ONE_ON_ONE_OFF, KEY_TWO_ON_TWO_OFF, KEY_DAY_SHIFT
    )

    @JvmStatic
    fun shortLabel(code: Int): String = when (code) {
        ShiftType.MORNING   -> "\u65e9"
        ShiftType.AFTERNOON -> "\u4e2d"
        ShiftType.NIGHT     -> "\u665a"
        ShiftType.REST      -> "\u4f11"
        else -> "?"
    }

    /**
     * Xiaomi-style badge colors (ARGB). The caller is responsible for
     * applying these; if [badgeColorRes] is ignored, defaults to
     * colorOnSurfaceVariant to stay theme-compliant.
     */
    @ColorInt
    @JvmStatic
    fun badgeColor(code: Int): Int = when (code) {
        ShiftType.MORNING   -> 0xFF2E7D32.toInt() // green-800
        ShiftType.AFTERNOON -> 0xFFEF6C00.toInt() // orange-800
        ShiftType.NIGHT     -> 0xFF3949AB.toInt() // indigo-700
        ShiftType.REST      -> 0xFF9E9E9E.toInt() // grey-500
        else                -> 0xFF9E9E9E.toInt()
    }

    @JvmStatic
    fun cycleForKey(key: String?): IntArray? = when (key) {
        KEY_THREE -> intArrayOf(
            ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT
        )
        KEY_FOUR_THREE -> intArrayOf(
            ShiftType.MORNING, ShiftType.MORNING,
            ShiftType.AFTERNOON, ShiftType.AFTERNOON,
            ShiftType.NIGHT, ShiftType.NIGHT,
            ShiftType.REST, ShiftType.REST
        )
        KEY_ONE_ON_ONE_OFF -> intArrayOf(ShiftType.MORNING, ShiftType.REST)
        KEY_TWO_ON_TWO_OFF -> intArrayOf(
            ShiftType.MORNING, ShiftType.MORNING,
            ShiftType.REST, ShiftType.REST
        )
        KEY_DAY_SHIFT -> intArrayOf(ShiftType.MORNING)
        else -> null
    }

    @JvmStatic
    fun displayName(key: String?): String = when (key) {
        KEY_THREE          -> "\u4e09\u73ed\u5012"
        KEY_FOUR_THREE     -> "\u56db\u73ed\u4e09\u5012"
        KEY_ONE_ON_ONE_OFF -> "\u4e0a\u4e00\u4f11\u4e00"
        KEY_TWO_ON_TWO_OFF -> "\u4e0a\u4e8c\u4f11\u4e8c"
        KEY_DAY_SHIFT      -> "\u5e38\u767d\u73ed"
        else -> ""
    }
}
