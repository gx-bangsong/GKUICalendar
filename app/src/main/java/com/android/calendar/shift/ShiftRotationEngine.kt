package com.android.calendar.shift

import com.android.calendar.shift.db.ShiftOverride
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule

object ShiftRotationEngine {

    fun getShiftForDay(
        julianDay: Int,
        activeRule: ShiftRotationRule?,
        presets: Map<Long, ShiftPreset>,
        overrides: Map<Int, Long>
    ): ShiftPreset? {
        // 1. Check Overrides first
        if (overrides.containsKey(julianDay)) {
            val presetId = overrides[julianDay]!!
            return if (presetId == 0L) null else presets[presetId]
        }

        // 2. Check Active Rule
        if (activeRule == null || activeRule.patternPresetIds.isEmpty()) return null

        val ids = activeRule.patternPresetIds.split(",").map { it.toLong() }
        val daysDiff = julianDay - activeRule.anchorJulianDay

        // Handle negative days if needed, but usually we start from anchor
        if (daysDiff < 0) return null

        val presetId = ids[(daysDiff % ids.size).toInt()]
        return if (presetId == 0L) null else presets[presetId]
    }

    // Helper for batch generation (e.g. for preview)
    fun generateShiftsForRange(
        startJulianDay: Int,
        endJulianDay: Int,
        activeRule: ShiftRotationRule?,
        presets: Map<Long, ShiftPreset>,
        overrides: Map<Int, Long>
    ): Map<Int, ShiftPreset> {
        val result = mutableMapOf<Int, ShiftPreset>()
        for (jd in startJulianDay..endJulianDay) {
            getShiftForDay(jd, activeRule, presets, overrides)?.let {
                result[jd] = it
            }
        }
        return result
    }
}
