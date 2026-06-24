package com.android.calendar.shift

object ShiftRotationEngine {

    fun generateFromPattern(
        anchorJulianDay: Int,
        numDays: Int = 365
    ): List<Int> {
        val days = mutableListOf<Int>()
        for (i in 0 until numDays) {
            days.add(anchorJulianDay + i)
        }
        return days
    }

    fun generatePattern(
        anchorJulianDay: Int,
        daysOn: Int,
        daysOff: Int,
        targetDurationDays: Int = 365
    ): Set<Int> {
        val pattern = mutableListOf<Boolean>()
        repeat(daysOn) { pattern.add(true) }
        repeat(daysOff) { pattern.add(false) }

        val workDays = mutableSetOf<Int>()
        if (pattern.isEmpty()) return workDays

        for (i in 0 until targetDurationDays) {
            val currentJulianDay = anchorJulianDay + i
            if (pattern[i % pattern.size]) {
                workDays.add(currentJulianDay)
            }
        }
        return workDays
    }
}
