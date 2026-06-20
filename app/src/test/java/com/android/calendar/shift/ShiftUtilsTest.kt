package com.android.calendar.shift

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftUtilsTest {

    @Test
    fun testFormatTitle() {
        assertEquals("早班", ShiftUtils.formatTitle("早班"))
        assertEquals("Shift: Working", ShiftUtils.formatTitle("Working"))
        assertEquals("Shift: 123", ShiftUtils.formatTitle("123"))
        assertEquals("Shift A", ShiftUtils.formatTitle("Shift A"))
    }

    @Test
    fun testFormatDescription() {
        assertEquals("Alarm: -90", ShiftUtils.formatDescription(90, false))
        assertEquals("Alarm: -45\n#IgnoreHoliday", ShiftUtils.formatDescription(45, true))
    }
}
