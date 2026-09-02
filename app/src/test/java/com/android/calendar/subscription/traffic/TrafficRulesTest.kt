/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.traffic

import com.android.calendar.subscription.traffic.data.TrafficRules
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficRulesTest {

    private fun jd(y: Int, m: Int, d: Int) = TrafficRules.julianDayOf(y, m, d)

    @Test fun weekdayMapping() {
        // 2025-09-29 was a Monday.
        assertEquals(1, TrafficRules.weekDayOf(jd(2025, 9, 29)))
        assertEquals(6, TrafficRules.weekDayOf(jd(2025, 10, 4)))
        assertEquals(0, TrafficRules.weekDayOf(jd(2025, 10, 5)))
    }

    @Test fun weekendsAreNeverRestricted() {
        val sat = jd(2025, 10, 4)
        assertTrue(TrafficRules.isWeekend(sat))
        assertNull(TrafficRules.restrictedDigits(sat, 0))
        assertFalse(TrafficRules.isRestricted(TrafficRules.MODE_TAIL_NUMBER, 4, 3, sat, 4))
    }

    @Test fun beijingAutumn2025Rotation() {
        // 2025-09-29 .. 2025-12-28: Mon..Fri = 4/9, 5/0, 1/6, 2/7, 3/8 (offset 3).
        val monday = jd(2025, 9, 29)
        val offset = TrafficRules.defaultGroupOffset(monday)
        assertEquals(3, offset)
        assertArrayEquals(intArrayOf(4, 9), TrafficRules.restrictedDigits(monday, offset))
        assertArrayEquals(intArrayOf(5, 0), TrafficRules.restrictedDigits(monday + 1, offset))
        assertArrayEquals(intArrayOf(1, 6), TrafficRules.restrictedDigits(monday + 2, offset))
        assertArrayEquals(intArrayOf(2, 7), TrafficRules.restrictedDigits(monday + 3, offset))
        assertArrayEquals(intArrayOf(3, 8), TrafficRules.restrictedDigits(monday + 4, offset))
    }

    @Test fun summer2026Rotation() {
        // 2026-06-29 .. 2026-09-27: Mon..Fri = 1/6, 2/7, 3/8, 4/9, 5/0 (offset 0).
        val monday = jd(2026, 6, 29)
        assertEquals(0, TrafficRules.defaultGroupOffset(monday))
        assertArrayEquals(intArrayOf(1, 6), TrafficRules.restrictedDigits(monday, 0))
    }

    @Test fun oddEvenMode() {
        val wed = jd(2025, 10, 15) // odd day of month, a weekday
        assertTrue(TrafficRules.isRestricted(TrafficRules.MODE_ODD_EVEN, 3, 0, wed, 15))
        assertFalse(TrafficRules.isRestricted(TrafficRules.MODE_ODD_EVEN, 4, 0, wed, 15))
    }

    @Test fun tailNumberMatchesUserPlate() {
        val monday = jd(2025, 9, 29)
        assertTrue(TrafficRules.isRestricted(TrafficRules.MODE_TAIL_NUMBER, 9, 3, monday, 29))
        assertFalse(TrafficRules.isRestricted(TrafficRules.MODE_TAIL_NUMBER, 5, 3, monday, 29))
    }
}
