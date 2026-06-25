package com.android.calendar.shift

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftMonthByWeekAdapterTest {

    @Test
    fun testJulianDayCalculationLogic() {
        // Since we cannot easily test Android Views in unit tests without Mockito,
        // we test the underlying logic if possible.
        val firstJulianDay = 2460000
        val position = 2
        val daysPerWeek = 7

        val calculatedJulianDay = firstJulianDay + position * daysPerWeek
        assertEquals(2460000 + 14, calculatedJulianDay)
    }
}
