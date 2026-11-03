/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import com.android.calendar.lunar.LunarHelper
import com.android.calendar.lunar.LunarMode

/**
 * Lunar labels for one month grid. Each day is decided by the project's own rule,
 * [LunarHelper.getLunarInfo], which is also what the classic month view uses. This object
 * adds no lunar rule of its own. It collects the days that the rule returns text for, and
 * leaves out the days it hides. OFF returns nothing, CONTEXTUAL returns only window days,
 * and ALWAYS returns every day.
 */
object MonthLunarLabels {

    /** Text for one day. [emphasized] is true on the festival or solar-term day itself. */
    data class Label(val text: String, val emphasized: Boolean)

    @JvmStatic
    fun build(
        firstJulian: Int,
        days: Int,
        mode: LunarMode,
        festivals: Set<String>,
        includeJieqi: Boolean,
        fullLabel: Boolean,
        todayJulian: Int
    ): Map<Int, Label> {
        val labels = HashMap<Int, Label>()
        for (day in firstJulian until firstJulian + days) {
            val info = LunarHelper.getLunarInfo(
                day, mode, festivals, includeJieqi, fullLabel, todayJulian
            ) ?: continue
            labels[day] = Label(info.shortText, info.isFestival)
        }
        return labels
    }
}
