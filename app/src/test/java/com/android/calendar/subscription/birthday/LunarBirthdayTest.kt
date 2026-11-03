/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.birthday

import com.android.calendar.subscription.birthday.data.LunarBirthday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LunarBirthdayTest {

    @Test fun roundTripsThroughSerialization() {
        val items = listOf(
            LunarBirthday("\u5988\u5988", 8, 15),
            LunarBirthday("Dad", 1, 1)
        )
        val raw = LunarBirthday.serializeAll(items)
        assertEquals(items, LunarBirthday.parseAll(raw))
    }

    @Test fun emptyInputYieldsEmptyList() {
        assertEquals(emptyList<LunarBirthday>(), LunarBirthday.parseAll(null))
        assertEquals(emptyList<LunarBirthday>(), LunarBirthday.parseAll(""))
    }

    @Test fun rejectsMalformedRows() {
        assertNull(LunarBirthday.parse("8|15"))
        assertNull(LunarBirthday.parse("13|15|Bad month"))
        assertNull(LunarBirthday.parse("8|31|Bad day"))
        assertNull(LunarBirthday.parse("8|15|   "))
    }

    @Test fun namesWithPipesKeepTheirTail() {
        val b = LunarBirthday.parse("3|3|A|B")
        assertEquals("A|B", b?.name)
        assertEquals(3, b?.lunarMonth)
    }

    @Test fun newlinesInNamesAreNeutralized() {
        val raw = LunarBirthday("a\nb", 2, 2).serialize()
        assertEquals("a b", LunarBirthday.parse(raw)?.name)
    }
}
