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
import com.nlf.calendar.Solar
import com.nlf.calendar.LunarYear

/** `pref_lunar_mode` values. */
enum class LunarMode {
    OFF,
    /** Only days inside a festival reveal window show lunar text. */
    CONTEXTUAL,
    /** Every day shows lunar text (festival days still get the chip). */
    ALWAYS
}

/** Immutable render instruction for one day cell. */
data class LunarInfo(
    /** Text drawn below the solar day number. */
    val shortText: String,
    /** true on the festival/jieqi day itself (EMPHASIZED: chip + bold). */
    val isFestival: Boolean,
    /** Display name of the festival when [isFestival], else null. */
    val festivalName: String?,
    /** Festival key (or "jieqi") when [isFestival], else null. */
    val festivalKey: String?
)

/**
 * Core of the "contextual reveal" lunar calendar (Smartisan-inspired).
 *
 * A day is HIDDEN (returns `null`) unless it falls inside the reveal window
 * of one of the enabled festivals (or the mode is [LunarMode.ALWAYS]). Window
 * rules:
 *
 *  * 春节 Spring Festival: 15 days before .. 7 days after (除夕 shares it)
 *  * every other festival: 5 days before .. 3 days after
 *  * 24 solar terms: 5/3, only when the jieqi toggle is on
 *
 * All heavy [Lunar]/[Solar] work is avoided entirely for HIDDEN days: the
 * window membership test is a binary search over a precomputed [IntArray]
 * owned by [LunarWindow].
 */
object LunarHelper {

    // Preference keys (values are locale-independent; entry labels are localized).
    const val KEY_SPRING = "spring"
    const val KEY_CHUXI = "chuxi"
    const val KEY_YUANXIAO = "yuanxiao"
    const val KEY_QINGMING = "qingming"
    const val KEY_DUANWU = "duanwu"
    const val KEY_QIXI = "qixi"
    const val KEY_ZHONGQIU = "zhongqiu"
    const val KEY_CHONGYANG = "chongyang"
    const val KEY_LABA = "laba"
    const val KEY_JIEQI = "jieqi"

    /** Festivals offered in the MultiSelectListPreference, in display order. */
    @JvmField
    val SELECTABLE_FESTIVALS: List<String> = listOf(
        KEY_SPRING, KEY_YUANXIAO, KEY_QINGMING, KEY_DUANWU,
        KEY_QIXI, KEY_ZHONGQIU, KEY_CHONGYANG, KEY_LABA
    )

    /** Default for `pref_lunar_festivals`: all eight festivals enabled. */
    @JvmField
    val DEFAULT_FESTIVALS: Set<String> = SELECTABLE_FESTIVALS.toSet()

    /** Minimal (Smartisan-style) display names; the "节" suffix is dropped. */
    private val FESTIVAL_NAMES: Map<String, String> = mapOf(
        KEY_SPRING to "春节", KEY_CHUXI to "除夕", KEY_YUANXIAO to "元宵",
        KEY_QINGMING to "清明", KEY_DUANWU to "端午", KEY_QIXI to "七夕",
        KEY_ZHONGQIU to "中秋", KEY_CHONGYANG to "重阳", KEY_LABA to "腊八"
    )

    /** Lunar month/day of each date-fixed festival (清明 comes from the jieqi table). */
    private val LUNAR_FESTIVAL_DAYS: Map<String, Pair<Int, Int>> = mapOf(
        KEY_SPRING to (1 to 1), KEY_YUANXIAO to (1 to 15), KEY_DUANWU to (5 to 5),
        KEY_QIXI to (7 to 7), KEY_ZHONGQIU to (8 to 15),
        KEY_CHONGYANG to (9 to 9), KEY_LABA to (12 to 8)
    )

    /** The 24 real solar-term names (Chinese keys of the per-year jieqi table). */
    private val TERM_NAMES: Set<String> = Lunar.JIE_QI.toSet()

    /** Chinese month names used for the first day of a lunar month. */
    @JvmField
    val CN_MONTH: Array<String> = arrayOf(
        "", "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    /** Chinese day names, 1-based ([CN_DAY][1] == "初一"). */
    @JvmField
    val CN_DAY: Array<String> = arrayOf(
        "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    /** Days of reveal window before a festival: 春节 15, everything else 5. */
    @JvmStatic
    fun daysBefore(festivalKey: String): Int =
        if (festivalKey == KEY_SPRING) 15 else 5

    /** Parses a `pref_lunar_mode` value; anything unknown means OFF. */
    @JvmStatic
    fun parseMode(value: String?): LunarMode = when (value) {
        "contextual" -> LunarMode.CONTEXTUAL
        "always" -> LunarMode.ALWAYS
        else -> LunarMode.OFF
    }

    /** Days of reveal window after a festival: 春节 7, everything else 3. */
    @JvmStatic
    fun daysAfter(festivalKey: String): Int =
        if (festivalKey == KEY_SPRING) 7 else 3

    // ---------------------------------------------------------------------
    // Julian day <-> civil date (Gregorian). Matches Time.getJulianDay():
    // ymdToJulianDay(1970, 1, 1) == 2440588.
    // ---------------------------------------------------------------------

    @JvmStatic
    fun ymdToJulianDay(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    /** Returns [year, month, day] for a julian day. */
    @JvmStatic
    fun julianDayToYmd(julianDay: Int): IntArray {
        var a = julianDay + 32044
        val b = (4 * a + 3) / 146097
        a -= 146097 * b / 4
        val c = (4 * a + 3) / 1461
        a -= 1461 * c / 4
        val e = (5 * a + 2) / 153
        val day = a - (153 * e + 2) / 5 + 1
        val month = e + 3 - 12 * (e / 10)
        val year = 100 * b + c - 4800 + e / 10
        return intArrayOf(year, month, day)
    }

    @JvmStatic
    fun todayJulianDay(): Int {
        val ms = System.currentTimeMillis()
        return 2440588 + Math.floorDiv(ms, 86400000L).toInt()
    }

    // ---------------------------------------------------------------------
    // Day lookup
    // ---------------------------------------------------------------------

    /**
     * Single entry point used by the month view. Returns what to render for
     * [julianDay] under the state machine
     * HIDDEN -> VISIBLE -> EMPHASIZED, or `null` for HIDDEN days.
     */
    @JvmStatic
    @JvmOverloads
    fun getLunarInfo(
        julianDay: Int,
        mode: LunarMode,
        enabledFestivals: Set<String>,
        includeJieqi: Boolean,
        fullLabel: Boolean = false,
        todayJulianDay: Int = this.todayJulianDay()
    ): LunarInfo? {
        if (mode == LunarMode.OFF || enabledFestivals.isEmpty() && !includeJieqi) {
            return null
        }
        val settings = LunarWindow.Settings(enabledFestivals, includeJieqi)
        LunarCache.onSettingsChanged(settings.signature() + "|" + fullLabel)
        val context = LunarWindow.findFestivalContext(julianDay, todayJulianDay, settings)
        if (context != null && context.festivalKey != null) {
            // EMPHASIZED: the festival/jieqi day itself.
            var info = LunarCache.get(julianDay)
            if (info == null) {
                info = LunarInfo(context.festivalName!!, true, context.festivalName,
                    context.festivalKey)
                LunarCache.put(julianDay, info)
            }
            return info
        }
        if (context == null && mode == LunarMode.CONTEXTUAL) {
            // HIDDEN: cheapest path, nothing was computed or allocated.
            return null
        }
        // VISIBLE (inside a window, or ALWAYS mode).
        var info = LunarCache.get(julianDay)
        if (info == null) {
            info = LunarInfo(getLunarDayText(julianDay, fullLabel), false, null, null)
            LunarCache.put(julianDay, info)
        }
        return info
    }

    /**
     * Window-only variant: returns `null` unless [julianDay] lies inside an
     * enabled festival window. This is the perf-critical contract - outside a
     * window there is no Solar/Lunar work at all.
     */
    @JvmStatic
    @JvmOverloads
    fun getLunarInfoIfInWindow(
        julianDay: Int,
        enabledFestivals: Set<String>,
        includeJieqi: Boolean,
        fullLabel: Boolean = false,
        todayJulianDay: Int = this.todayJulianDay()
    ): LunarInfo? =
        getLunarInfo(
            julianDay, LunarMode.CONTEXTUAL, enabledFestivals, includeJieqi,
            fullLabel, todayJulianDay
        )

    /** Compact lunar label: "初一" shows the month name ("正月"), leap months prefixed. */
    @JvmStatic
    @JvmOverloads
    fun getLunarDayText(julianDay: Int, fullLabel: Boolean = false): String {
        val ymd = julianDayToYmd(julianDay)
        val lunar = Solar.fromYmd(ymd[0], ymd[1], ymd[2]).lunar
        val month = lunar.month
        val day = lunar.day
        val monthName = (if (month < 0) "闰" else "") + CN_MONTH[kotlin.math.abs(month)]
        return when {
            fullLabel -> monthName + CN_DAY[day]
            day == 1 -> monthName
            else -> CN_DAY[day]
        }
    }

    // ---------------------------------------------------------------------
    // Window construction (called by LunarWindow.rebuild)
    // ---------------------------------------------------------------------

    /**
     * Scans the lunar years covering `rangeStart..rangeEnd` and reports every
     * festival/jieqi anchor day. Called only when the precomputed window is
     * stale (cold start, settings change, scrolled out of coverage) - never
     * per draw.
     *
     * @return map of julian day to festival marker
     */
    internal fun collectFestivalDays(
        rangeStart: Int,
        rangeEnd: Int,
        settings: LunarWindow.Settings
    ): Map<Int, Marker> {
        val anchors = HashMap<Int, Marker>(96)
        val firstYear = lunarYearOf(rangeStart) - 1
        val lastYear = lunarYearOf(rangeEnd) + 1
        for (lunarYear in firstYear..lastYear) {
            // Solar terms: mirrors Lunar.computeJieQi() exactly.
            val julianDays = LunarYear.fromYear(lunarYear).jieQiJulianDays
            for (i in julianDays.indices) {
                val term = Lunar.JIE_QI_IN_USE[i]
                if (term !in TERM_NAMES) continue
                val solar = Solar.fromJulianDay(julianDays[i])
                val jd = ymdToJulianDay(solar.year, solar.month, solar.day)
                when {
                    term == "清明" && KEY_QINGMING in settings.enabledFestivals ->
                        anchors[jd] = Marker(KEY_QINGMING, "清明")
                    settings.includeJieqi -> anchors[jd] = Marker(KEY_JIEQI, term)
                }
            }
            // Date-fixed lunar festivals.
            for ((key, md) in LUNAR_FESTIVAL_DAYS) {
                if (key !in settings.enabledFestivals) continue
                val solar = Lunar.fromYmd(lunarYear, md.first, md.second).solar
                val jd = ymdToJulianDay(solar.year, solar.month, solar.day)
                if (jd in rangeStart..rangeEnd) {
                    anchors[jd] = Marker(key, FESTIVAL_NAMES.getValue(key))
                }
                if (key == KEY_SPRING) {
                    // 除夕 shares the Spring Festival window (no anchor of its own).
                    val chuxiJd = jd - 1
                    if (chuxiJd in rangeStart..rangeEnd) {
                        anchors[chuxiJd] = Marker(KEY_CHUXI, FESTIVAL_NAMES.getValue(KEY_CHUXI))
                    }
                }
            }
        }
        return anchors
    }

    private fun lunarYearOf(julianDay: Int): Int {
        val ymd = julianDayToYmd(julianDay)
        return Solar.fromYmd(ymd[0], ymd[1], ymd[2]).lunar.year
    }

    /** Internal anchor used while (re)building the active-day index. */
    internal data class Marker(val key: String, val name: String)
}
