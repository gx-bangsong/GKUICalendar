package com.android.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmanacInfoTest {

    private fun jd(y: Int, m: Int, d: Int) = LunarHelper.ymdToJulianDay(y, m, d)

    @Test
    fun solarFieldsMatchTheRequestedDay() {
        val info = AlmanacInfo.forJulianDay(jd(2026, 9, 22))
        assertEquals("22", info.solarDay)
        assertEquals("2026年9月", info.solarMonth)
    }

    /** Weekday derives from the julian day, so an off-by-one is easy to miss. */
    @Test
    fun weekdayIsCorrect() {
        // 2026-09-22 is a Tuesday; 2026-09-20 a Sunday.
        assertEquals("星期二", AlmanacInfo.forJulianDay(jd(2026, 9, 22)).weekday)
        assertEquals("星期日", AlmanacInfo.forJulianDay(jd(2026, 9, 20)).weekday)
        // 1970-01-01 was a Thursday - the epoch anchor of the calculation.
        assertEquals("星期四", AlmanacInfo.forJulianDay(jd(1970, 1, 1)).weekday)
    }

    @Test
    fun ganZhiHasThreePillars() {
        val info = AlmanacInfo.forJulianDay(jd(2026, 9, 22))
        assertTrue(info.ganZhi, info.ganZhi.contains("年"))
        assertTrue(info.ganZhi, info.ganZhi.contains("月"))
        assertTrue(info.ganZhi, info.ganZhi.contains("日"))
    }

    @Test
    fun lunarNewYearIsReportedAsAFestival() {
        // 2026-02-17 is 正月初一 (春节).
        val info = AlmanacInfo.forJulianDay(jd(2026, 2, 17))
        assertEquals("正月初一", info.lunarDate)
        assertNotNull(info.festival)
    }

    /** lunar-java returns ["无"] rather than an empty list; it must be stripped. */
    @Test
    fun noneSentinelIsStrippedFromYiAndJi() {
        for (offset in 0 until 60) {
            val info = AlmanacInfo.forJulianDay(jd(2026, 1, 1) + offset)
            assertTrue("宜 kept sentinel", info.yi.none { it == "无" })
            assertTrue("忌 kept sentinel", info.ji.none { it == "无" })
        }
    }

    @Test
    fun entriesAreCappedByMaxEntries() {
        for (offset in 0 until 40) {
            val info = AlmanacInfo.forJulianDay(jd(2026, 3, 1) + offset, maxEntries = 3)
            assertTrue(info.yi.size <= 3)
            assertTrue(info.ji.size <= 3)
        }
    }

    @Test
    fun emptyListsFallBackToTheSuppliedLabel() {
        val empty = AlmanacInfo(
            solarDay = "1", solarMonth = "2026年1月", weekday = "星期四",
            lunarDate = "冬月十三", ganZhi = "乙巳年 戊子月 甲子日", animal = "蛇",
            jieQi = null, festival = null, yi = emptyList(), ji = emptyList()
        )
        assertEquals("无", empty.yiText("无"))
        assertEquals("无", empty.jiText("无"))
        // With no term or festival the badge falls back to the zodiac year.
        assertEquals("蛇年", empty.badge())
    }

    @Test
    fun badgePrefersJieQiThenFestival() {
        val withTerm = AlmanacInfo(
            solarDay = "22", solarMonth = "2026年9月", weekday = "星期二",
            lunarDate = "八月十二", ganZhi = "x", animal = "马",
            jieQi = "秋分", festival = "中秋节", yi = emptyList(), ji = emptyList()
        )
        assertEquals("秋分", withTerm.badge())
        assertEquals("中秋节", withTerm.copy(jieQi = null).badge())
    }
}
