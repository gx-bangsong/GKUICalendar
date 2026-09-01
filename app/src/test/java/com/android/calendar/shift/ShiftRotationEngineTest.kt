package com.android.calendar.shift

import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftRotationEngineTest {

    @Test
    fun testGenerateShifts_WithRuleAndOverrides() {
        val anchor = 2460000 // A sample Julian Day
        val preset1 = ShiftPreset(id = 1, title = "Day", startTime = 480, endTime = 1020, alarmOffset = 30, ignoreHoliday = false, color = 0xFF0000)
        val preset2 = ShiftPreset(id = 2, title = "Night", startTime = 1200, endTime = 1440, alarmOffset = 60, ignoreHoliday = true, color = 0x0000FF)
        val presets = mapOf(1L to preset1, 2L to preset2)

        // Pattern: Day, Day, Rest (1, 1, 0)
        val rule = ShiftRotationRule(anchorJulianDay = anchor, patternPresetIds = "1,1,0")

        // Overrides: Day 2460002 (which should be Rest) set to Night (2)
        val overrides = mapOf(2460002 to 2L)

        val results = ShiftRotationEngine.generateShiftsForRange(anchor, anchor + 2, rule, presets, overrides)

        // Day 0: 2460000 -> pattern index 0 -> Preset 1
        assertEquals(1L, results[2460000]?.id)

        // Day 1: 2460001 -> pattern index 1 -> Preset 1
        assertEquals(1L, results[2460001]?.id)

        // Day 2: 2460002 -> pattern index 2 (Rest) BUT override exists -> Preset 2
        assertEquals(2L, results[2460002]?.id)
    }

    @Test
    fun testGenerateShifts_WithRestOverride() {
        val anchor = 2460000
        val preset1 = ShiftPreset(id = 1, title = "Day", startTime = 480, endTime = 1020, alarmOffset = 30, ignoreHoliday = false, color = 0xFF0000)
        val presets = mapOf(1L to preset1)
        val rule = ShiftRotationRule(anchorJulianDay = anchor, patternPresetIds = "1") // Every day is Day

        // Override day 2460000 to be Rest (0L)
        val overrides = mapOf(2460000 to 0L)

        val results = ShiftRotationEngine.generateShiftsForRange(anchor, anchor, rule, presets, overrides)

        // Should be empty because 0L (Rest) is filtered out in generateShiftsForRange
        assertEquals(false, results.containsKey(2460000))
    }
}
