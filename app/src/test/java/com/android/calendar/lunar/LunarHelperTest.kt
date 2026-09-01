/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.calendar.lunar

import com.nlf.calendar.Lunar
import com.nlf.calendar.LunarYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Festival window and lunar text tests for the contextual reveal engine.
 *
 * Known dates (2020-2030) are hard-coded on purpose: they pin the vendored
 * lunar-java tables, so a bad strip or table regression fails loudly here.
 */
class LunarHelperTest {

    private val all = LunarHelper.DEFAULT_FESTIVALS

    private fun jd(year: Int, month: Int, day: Int): Int =
        LunarHelper.ymdToJulianDay(year, month, day)

    /** Spring Festival solar dates, 2020-2030 (hard-coded ground truth). */
    private val springDates: Map<Int, Triple<Int, Int, String>> = mapOf(
        2020 to Triple(1, 25, "2020-01-25"),
        2021 to Triple(2, 12, "2021-02-12"),
        2022 to Triple(2, 1, "2022-02-01"),
        2023 to Triple(1, 22, "2023-01-22"),
        2024 to Triple(2, 10, "2024-02-10"),
        2025 to Triple(1, 29, "2025-01-29"),
        2026 to Triple(2, 17, "2026-02-17"),
        2027 to Triple(2, 6, "2027-02-06"),
        2028 to Triple(1, 26, "2028-01-26"),
        2029 to Triple(2, 13, "2029-02-13"),
        2030 to Triple(2, 3, "2030-02-03")
    )

    // ---------------------------------------------------------------------
    // Julian day conversion
    // ---------------------------------------------------------------------

    @Test
    fun julianDay_epochIs2440588() {
        assertEquals(2440588, LunarHelper.ymdToJulianDay(1970, 1, 1))
    }

    @Test
    fun julianDay_roundTrip() {
        val samples = listOf(
            Triple(2000, 2, 29), Triple(2016, 12, 31), Triple(2024, 2, 10),
            Triple(2030, 6, 15), Triple(2100, 3, 1)
        )
        for ((y, m, d) in samples) {
            val ymd = LunarHelper.julianDayToYmd(LunarHelper.ymdToJulianDay(y, m, d))
            assertTrue("round trip $y-$m-$d", ymd[0] == y && ymd[1] == m && ymd[2] == d)
        }
    }

    // ---------------------------------------------------------------------
    // Spring Festival windows (15 before / 7 after)
    // ---------------------------------------------------------------------

    @Test
    fun springFestivalDates_matchVendoredTables() {
        for ((year, expected) in springDates) {
            val solar = Lunar.fromYmd(year, 1, 1).solar
            assertEquals("春节 $year", expected.third, solar.toYmd())
        }
    }

    @Test
    fun springWindow_entryDayIsVisible_dayBeforeEntryIsHidden() {
        val spring = jd(2024, 2, 10)
        val today = jd(2024, 1, 1)
        // 15 days before: first visible day (2024-01-26 = 腊月十六).
        val entry = LunarHelper.getLunarInfoIfInWindow(spring - 15, all, false, false, today)
        assertNotNull("entry day is visible", entry)
        assertFalse("entry day is not the festival itself", entry!!.isFestival)
        assertEquals("十六", entry.shortText)
        val before = LunarHelper.getLunarInfoIfInWindow(spring - 16, all, false, false, today)
        assertNull("day before entry is hidden", before)
    }

    @Test
    fun springWindow_exitDayIsVisible_dayAfterExitIsHidden() {
        val spring = jd(2024, 2, 10)
        val today = jd(2024, 1, 1)
        val exit = LunarHelper.getLunarInfoIfInWindow(spring + 7, all, false, false, today)
        assertNotNull("exit day is visible", exit)
        assertFalse("exit day is not the festival itself", exit!!.isFestival)
        assertEquals("初八", exit.shortText)
        val after = LunarHelper.getLunarInfoIfInWindow(spring + 8, all, false, false, today)
        assertNull("day after exit is hidden", after)
    }

    @Test
    fun springFestivalDay_isEmphasized() {
        val spring = jd(2024, 2, 10)
        val info = LunarHelper.getLunarInfoIfInWindow(spring, all, false, false, jd(2024, 1, 1))
        assertNotNull(info)
        assertTrue(info!!.isFestival)
        assertEquals("春节", info.festivalName)
        assertEquals(LunarHelper.KEY_SPRING, info.festivalKey)
        assertEquals("春节", info.shortText)
    }

    @Test
    fun springWindow_spans2020Through2030() {
        for ((year, expected) in springDates) {
            val spring = jd(year, expected.first, expected.second)
            val today = jd(year, 1, 1)
            val inside = LunarHelper.getLunarInfoIfInWindow(spring - 15, all, false, false, today)
            assertNotNull("window opens 15 days before 春节 $year", inside)
            val outside = LunarHelper.getLunarInfoIfInWindow(spring - 16, all, false, false, today)
            assertNull("window closed 16 days before 春节 $year", outside)
        }
    }

    // ---------------------------------------------------------------------
    // 除夕 (shares the Spring Festival window)
    // ---------------------------------------------------------------------

    @Test
    fun chuxi_isEmphasizedOneDayBeforeSpring() {
        val chuxi2024 = LunarHelper.getLunarInfoIfInWindow(
            jd(2024, 2, 9), all, false, false, jd(2024, 1, 1))
        assertNotNull(chuxi2024)
        assertTrue(chuxi2024!!.isFestival)
        assertEquals("除夕", chuxi2024.festivalName)
        assertEquals(LunarHelper.KEY_CHUXI, chuxi2024.festivalKey)

        val chuxi2025 = LunarHelper.getLunarInfoIfInWindow(
            jd(2025, 1, 28), all, false, false, jd(2025, 1, 1))
        assertNotNull(chuxi2025)
        assertEquals("除夕", chuxi2025!!.festivalName)
    }

    @Test
    fun chuxi_usesSpringWindowBounds() {
        // 春节 2025-01-29 => shared window [2025-01-14, 2025-02-05].
        val today = jd(2025, 1, 1)
        assertNotNull(
            LunarHelper.getLunarInfoIfInWindow(jd(2025, 1, 14), all, false, false, today))
        assertNull(
            LunarHelper.getLunarInfoIfInWindow(jd(2025, 1, 13), all, false, false, today))
        assertNotNull(
            LunarHelper.getLunarInfoIfInWindow(jd(2025, 2, 5), all, false, false, today))
        // 2025-02-06 is also one day before the 元宵 window [02-07..02-15].
        assertNull(
            LunarHelper.getLunarInfoIfInWindow(jd(2025, 2, 6), all, false, false, today))
    }

    // ---------------------------------------------------------------------
    // Other festivals: 5 before / 3 after
    // ---------------------------------------------------------------------

    @Test
    fun midAutumnFestival_2024() {
        val festival = jd(2024, 9, 17) // 八月十五
        val today = jd(2024, 9, 1)
        val info = LunarHelper.getLunarInfoIfInWindow(festival, all, false, false, today)
        assertNotNull(info)
        assertTrue(info!!.isFestival)
        assertEquals("中秋", info.festivalName)
        assertEquals(LunarHelper.KEY_ZHONGQIU, info.festivalKey)
    }

    @Test
    fun midAutumnWindow_boundaries() {
        val festival = jd(2024, 9, 17)
        val today = jd(2024, 9, 1)
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(festival - 5, all, false, false, today))
        assertNull(LunarHelper.getLunarInfoIfInWindow(festival - 6, all, false, false, today))
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(festival + 3, all, false, false, today))
        assertNull(LunarHelper.getLunarInfoIfInWindow(festival + 4, all, false, false, today))
    }

    @Test
    fun allEightFestivals_2024_dates() {
        val today = jd(2024, 1, 1)
        val expected = listOf(
            Triple(jd(2024, 1, 18), "腊八", LunarHelper.KEY_LABA),
            Triple(jd(2024, 2, 9), "除夕", LunarHelper.KEY_CHUXI),
            Triple(jd(2024, 2, 10), "春节", LunarHelper.KEY_SPRING),
            Triple(jd(2024, 2, 24), "元宵", LunarHelper.KEY_YUANXIAO),
            Triple(jd(2024, 4, 4), "清明", LunarHelper.KEY_QINGMING),
            Triple(jd(2024, 6, 10), "端午", LunarHelper.KEY_DUANWU),
            Triple(jd(2024, 8, 10), "七夕", LunarHelper.KEY_QIXI),
            Triple(jd(2024, 9, 17), "中秋", LunarHelper.KEY_ZHONGQIU),
            Triple(jd(2024, 10, 11), "重阳", LunarHelper.KEY_CHONGYANG)
        )
        for ((day, name, key) in expected) {
            val info = LunarHelper.getLunarInfoIfInWindow(day, all, false, false, today)
            assertNotNull("$name 2024 is detected", info)
            assertTrue("$name 2024 is emphasized", info!!.isFestival)
            assertEquals("$name 2024 name", name, info.festivalName)
            assertEquals("$name 2024 key", key, info.festivalKey)
        }
    }

    @Test
    fun gapDayBetweenSpringAndYuanxiaoWindows_isHidden() {
        // 春节 2024 window ends 02-17, 元宵 (02-24) window starts 02-19.
        val today = jd(2024, 1, 1)
        assertNull(LunarHelper.getLunarInfoIfInWindow(jd(2024, 2, 18), all, false, false, today))
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(jd(2024, 2, 19), all, false, false, today))
    }

    // ---------------------------------------------------------------------
    // 清明 comes from the solar-term table, not the lunar month/day table
    // ---------------------------------------------------------------------

    @Test
    fun qingming_windowBoundaries() {
        val qingming = jd(2024, 4, 4)
        val today = jd(2024, 3, 1)
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(qingming - 5, all, false, false, today))
        assertNull(LunarHelper.getLunarInfoIfInWindow(qingming - 6, all, false, false, today))
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(qingming + 3, all, false, false, today))
        assertNull(LunarHelper.getLunarInfoIfInWindow(qingming + 4, all, false, false, today))
    }

    // ---------------------------------------------------------------------
    // Disabled festival returns null (window disappears entirely)
    // ---------------------------------------------------------------------

    @Test
    fun disabledFestival_hasNoWindow() {
        val festival = jd(2024, 9, 17)
        val today = jd(2024, 9, 1)
        val withoutZhongqiu = all.filterNot { it == LunarHelper.KEY_ZHONGQIU }.toSet()
        for (offset in -6..4) {
            assertNull(
                "offset $offset hidden when 中秋 disabled",
                LunarHelper.getLunarInfoIfInWindow(
                    festival + offset, withoutZhongqiu, false, false, today))
        }
        // Re-enabling must rebuild the window (settings change path).
        assertNotNull(
            LunarHelper.getLunarInfoIfInWindow(festival, all, false, false, today))
    }

    @Test
    fun allFestivalsDisabled_returnsNull() {
        val today = jd(2024, 1, 1)
        assertNull(LunarHelper.getLunarInfoIfInWindow(
            jd(2024, 2, 10), emptySet(), false, false, today))
    }

    // ---------------------------------------------------------------------
    // Solar terms toggle
    // ---------------------------------------------------------------------

    @Test
    fun jieqi_toggleControlsOrdinaryTerms() {
        // 霜降 2024-10-23: not inside any other festival window.
        val shuangJiang = jd(2024, 10, 23)
        val today = jd(2024, 10, 1)
        val off = LunarHelper.getLunarInfoIfInWindow(shuangJiang, all, false, false, today)
        assertNull("霜降 hidden when jieqi disabled", off)
        val on = LunarHelper.getLunarInfoIfInWindow(shuangJiang, all, true, false, today)
        assertNotNull("霜降 shown when jieqi enabled", on)
        assertTrue(on!!.isFestival)
        assertEquals("霜降", on.festivalName)
        assertEquals(LunarHelper.KEY_JIEQI, on.festivalKey)
    }

    @Test
    fun jieqi_windowBoundaries() {
        val shuangJiang = jd(2024, 10, 23)
        val today = jd(2024, 10, 1)
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(shuangJiang - 5, all, true, false, today))
        assertNull(LunarHelper.getLunarInfoIfInWindow(shuangJiang - 6, all, true, false, today))
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(shuangJiang + 3, all, true, false, today))
        assertNull(LunarHelper.getLunarInfoIfInWindow(shuangJiang + 4, all, true, false, today))
    }

    @Test
    fun qingmingStillWorksWithJieqiDisabled() {
        // 清明 is one of the 8 festivals, independent of the jieqi toggle.
        val info = LunarHelper.getLunarInfoIfInWindow(
            jd(2024, 4, 4), all, false, false, jd(2024, 3, 1))
        assertNotNull(info)
        assertEquals(LunarHelper.KEY_QINGMING, info!!.festivalKey)
    }

    // ---------------------------------------------------------------------
    // Leap month (闰四月 2020)
    // ---------------------------------------------------------------------

    @Test
    fun leapMonth_2020IsRunSiYue() {
        assertEquals(4, LunarYear.fromYear(2020).leapMonth)
    }

    @Test
    fun leapMonth_firstDayText() {
        assertEquals("2020-05-23", Lunar.fromYmd(2020, -4, 1).solar.toYmd())
        assertEquals("闰四月", LunarHelper.getLunarDayText(jd(2020, 5, 23)))
        assertEquals("初二", LunarHelper.getLunarDayText(jd(2020, 5, 24)))
        assertEquals("闰四月初一", LunarHelper.getLunarDayText(jd(2020, 5, 23), fullLabel = true))
    }

    @Test
    fun leapMonth_neverCarriesFestivals() {
        assertTrue(Lunar.fromYmd(2020, -4, 5).festivals.isEmpty())
    }

    // ---------------------------------------------------------------------
    // Lunar text rendering rules
    // ---------------------------------------------------------------------

    @Test
    fun firstDayOfLunarMonth_showsMonthName() {
        // 2024-09-03 is 八月初一 (中秋 09-17 is 八月十五).
        assertEquals("八月", LunarHelper.getLunarDayText(jd(2024, 9, 3)))
        assertEquals("十五", LunarHelper.getLunarDayText(jd(2024, 9, 17)))
        assertEquals("八月十五", LunarHelper.getLunarDayText(jd(2024, 9, 17), fullLabel = true))
    }

    @Test
    fun visibleDaysInsideWindow_showLunarDayText() {
        val today = jd(2024, 9, 1)
        // 2024-09-12: first day of the 中秋 window, 八月初十.
        val info = LunarHelper.getLunarInfoIfInWindow(jd(2024, 9, 12), all, false, false, today)
        assertNotNull(info)
        assertFalse(info!!.isFestival)
        assertNull(info.festivalKey)
        assertEquals("初十", info.shortText)
    }

    @Test
    fun alwaysMode_showsTextForEveryDay_outsideWindowsToo() {
        val today = jd(2024, 7, 1)
        // 2024-07-15: no window nearby (端午 window closed 06-13, 中秋 opens 09-12).
        val day = jd(2024, 7, 15)
        assertNull(LunarHelper.getLunarInfoIfInWindow(day, all, false, false, today))
        val always = LunarHelper.getLunarInfo(
            day, LunarMode.ALWAYS, all, false, false, today)
        assertNotNull(always)
        assertFalse(always!!.isFestival)
        assertTrue(always.shortText.isNotEmpty())
    }

    @Test
    fun offMode_returnsNull() {
        assertNull(LunarHelper.getLunarInfo(
            jd(2024, 2, 10), LunarMode.OFF, all, false, false, jd(2024, 1, 1)))
    }

    // ---------------------------------------------------------------------
    // Cache behaviour
    // ---------------------------------------------------------------------

    @Test
    fun cache_returnsSameValueForRepeatedQueries() {
        val today = jd(2024, 9, 1)
        val first = LunarHelper.getLunarInfoIfInWindow(jd(2024, 9, 12), all, false, false, today)
        val second = LunarHelper.getLunarInfoIfInWindow(jd(2024, 9, 12), all, false, false, today)
        assertNotNull(first)
        assertEquals("repeated query is stable", first, second)
    }

    @Test
    fun cache_invalidatedWhenSettingsChange() {
        val day = jd(2024, 9, 17)
        val today = jd(2024, 9, 1)
        val withZhongqiu = LunarHelper.getLunarInfoIfInWindow(day, all, false, false, today)
        val withoutZhongqiu = LunarHelper.getLunarInfoIfInWindow(
            day, all.filterNot { it == LunarHelper.KEY_ZHONGQIU }.toSet(), false, false, today)
        assertNotNull(withZhongqiu)
        assertNull(withoutZhongqiu)
    }

    // ---------------------------------------------------------------------
    // Window rebuild when scrolling far outside coverage
    // ---------------------------------------------------------------------

    @Test
    fun farQuery_rebuildsWindowAroundQuery() {
        // Snapshot built around 2024; then jump to 2029 春节.
        val today2024 = jd(2024, 1, 1)
        assertNotNull(LunarHelper.getLunarInfoIfInWindow(
            jd(2024, 2, 10), all, false, false, today2024))
        val spring2029 = jd(2029, 2, 13)
        val info = LunarHelper.getLunarInfoIfInWindow(spring2029, all, false, false, today2024)
        assertNotNull(info)
        assertEquals("春节", info!!.festivalName)
        // And back again.
        val back = LunarHelper.getLunarInfoIfInWindow(jd(2024, 2, 10), all, false, false, today2024)
        assertNotNull(back)
        assertTrue(back!!.isFestival)
    }
}
