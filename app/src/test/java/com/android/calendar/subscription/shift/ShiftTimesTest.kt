/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.shift

import com.android.calendar.subscription.shift.data.ShiftTimes
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftTimesTest {

    @Test fun formatsMinutesAsClockTime() {
        assertEquals("07:30", ShiftTimes.formatTime(7 * 60 + 30))
        assertEquals("00:00", ShiftTimes.formatTime(0))
        assertEquals("23:59", ShiftTimes.formatTime(23 * 60 + 59))
    }

    @Test fun formatsRanges() {
        assertEquals("08:00-20:00",
            ShiftTimes.formatRange(8 * 60, 20 * 60))
    }

    @Test fun sameDayDuration() {
        // 07:30 -> 15:30 is an 8 hour shift.
        assertEquals(8 * 60, ShiftTimes.durationMinutes(7 * 60 + 30, 15 * 60 + 30))
    }

    @Test fun nightShiftWrapsPastMidnight() {
        // 23:30 -> 07:30 is 8 hours, not a negative span.
        assertEquals(8 * 60, ShiftTimes.durationMinutes(23 * 60 + 30, 7 * 60 + 30))
    }

    @Test fun twelveHourShiftIsSupported() {
        assertEquals(12 * 60, ShiftTimes.durationMinutes(8 * 60, 20 * 60))
        assertEquals(12 * 60, ShiftTimes.durationMinutes(20 * 60, 8 * 60))
    }

    @Test fun outOfRangeMinutesWrapIntoTheDay() {
        assertEquals("01:00", ShiftTimes.formatTime(25 * 60))
        assertEquals("23:00", ShiftTimes.formatTime(-60))
    }
}
