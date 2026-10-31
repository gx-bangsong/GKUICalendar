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

import com.nlf.calendar.Solar

/**
 * One day of 万年历 almanac data, as rendered by the almanac widget.
 *
 * Pure data: no Android types, so it is unit-testable on the JVM.
 */
data class AlmanacInfo(
    /** Gregorian day number, e.g. "22". */
    val solarDay: String,
    /** Gregorian year/month heading, e.g. "2026年9月". */
    val solarMonth: String,
    /** Weekday, e.g. "星期二". */
    val weekday: String,
    /** Lunar date, e.g. "八月十二". */
    val lunarDate: String,
    /** Ganzhi line, e.g. "丙午年 丁酉月 戊辰日". */
    val ganZhi: String,
    /** Zodiac animal, e.g. "马". */
    val animal: String,
    /** Solar term for the day, or null when the day carries none. */
    val jieQi: String?,
    /** Festival name when the day is one, else null. */
    val festival: String?,
    /** 宜 entries; empty when the almanac has none. */
    val yi: List<String>,
    /** 忌 entries; empty when the almanac has none. */
    val ji: List<String>
) {
    companion object {

        /** lunar-java returns this sentinel rather than an empty list. */
        private const val NONE = "无"

        private val WEEKDAYS = arrayOf(
            "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"
        )

        /**
         * Builds the almanac entry for [julianDay].
         *
         * @param maxEntries cap on the 宜/忌 lists so the widget stays legible.
         */
        @JvmStatic
        @JvmOverloads
        fun forJulianDay(julianDay: Int, maxEntries: Int = 6): AlmanacInfo {
            val ymd = LunarHelper.julianDayToYmd(julianDay)
            val solar = Solar.fromYmd(ymd[0], ymd[1], ymd[2])
            val lunar = solar.lunar

            val jieQi = lunar.jieQi.takeIf { it.isNotEmpty() }
            // getFestivals() covers lunar festivals such as 春节 / 中秋.
            val festival = lunar.festivals.firstOrNull()

            // Julian day 2440588 is 1970-01-01, a Thursday (index 4).
            val weekdayIndex = Math.floorMod(julianDay - 2440588 + 4, 7)

            return AlmanacInfo(
                solarDay = ymd[2].toString(),
                solarMonth = "${ymd[0]}年${ymd[1]}月",
                weekday = WEEKDAYS[weekdayIndex],
                lunarDate = LunarHelper.getLunarDayText(julianDay, fullLabel = true),
                ganZhi = "${lunar.yearInGanZhi}年 ${lunar.monthInGanZhi}月 " +
                    "${lunar.dayInGanZhi}日",
                animal = lunar.animal,
                jieQi = jieQi,
                festival = festival,
                yi = clean(lunar.dayYi, maxEntries),
                ji = clean(lunar.dayJi, maxEntries)
            )
        }

        /** Drops the "无" sentinel and caps the list length. */
        private fun clean(values: List<String>?, maxEntries: Int): List<String> {
            if (values.isNullOrEmpty()) return emptyList()
            if (values.size == 1 && values[0] == NONE) return emptyList()
            return values.filter { it != NONE }.take(maxEntries)
        }
    }

    /** 宜 rendered as a single line, or "无" when there is nothing to show. */
    fun yiText(emptyLabel: String): String =
        if (yi.isEmpty()) emptyLabel else yi.joinToString(" ")

    /** 忌 rendered as a single line, or "无" when there is nothing to show. */
    fun jiText(emptyLabel: String): String =
        if (ji.isEmpty()) emptyLabel else ji.joinToString(" ")

    /**
     * Secondary heading: the solar term or festival when the day has one,
     * otherwise the zodiac year.
     */
    fun badge(): String = jieQi ?: festival ?: "${animal}年"
}
