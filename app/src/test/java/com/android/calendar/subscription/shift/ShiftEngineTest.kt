/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.shift

import com.android.calendar.subscription.shift.data.ShiftEngine
import com.android.calendar.subscription.shift.data.ShiftPresets
import com.android.calendar.subscription.shift.data.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftEngineTest {
    @Test fun anchorDayIsFirstInCycle() {
        val cycle = ShiftPresets.cycleForKey(ShiftPresets.KEY_THREE)!!
        assertEquals(ShiftType.MORNING, ShiftEngine.typeFor(cycle, 100, 100))
    }

    @Test fun wrapsAround() {
        val cycle = intArrayOf(1, 2, 3, 4)
        assertEquals(ShiftType.NIGHT, ShiftEngine.typeFor(cycle, 0, 2))
        assertEquals(ShiftType.REST, ShiftEngine.typeFor(cycle, 0, 3))
        assertEquals(ShiftType.MORNING, ShiftEngine.typeFor(cycle, 0, 4))
    }

    @Test fun beforeAnchorReturnsRest() {
        assertEquals(-1, ShiftEngine.typeFor(
            ShiftPresets.cycleForKey(ShiftPresets.KEY_THREE)!!, 100, 99))
    }

    @Test fun twoRestTwoWork() {
        // 2 days work, 2 days rest is a common pattern
        val cycle = intArrayOf(1, 1, 4, 4)
        assertEquals(ShiftType.MORNING, ShiftEngine.typeFor(cycle, 0, 0))
        assertEquals(ShiftType.MORNING, ShiftEngine.typeFor(cycle, 0, 1))
        assertEquals(ShiftType.REST, ShiftEngine.typeFor(cycle, 0, 2))
        assertEquals(ShiftType.REST, ShiftEngine.typeFor(cycle, 0, 3))
        assertEquals(ShiftType.MORNING, ShiftEngine.typeFor(cycle, 0, 4))
    }
}
