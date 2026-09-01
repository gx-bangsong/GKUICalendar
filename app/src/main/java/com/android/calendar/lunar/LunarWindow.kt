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


/**
 * Precomputed index of every "active" julian day (i.e. every day inside at
 * least one festival reveal window) for roughly the next 400 days.
 *
 * [findFestivalContext] is a binary search over a sorted [IntArray]: O(log n),
 * no allocation, no [com.nlf.calendar.Lunar] work. The snapshot is rebuilt
 * lazily when (a) the settings signature changes, or (b) a query falls outside
 * the covered range - which also keeps it fresh across days without needing a
 * WorkManager tick.
 */
object LunarWindow {

    /** How far past the anchor the index reaches. */
    private const val FORWARD_DAYS = 400

    /** Slack before the anchor so scrolling back a month stays covered. */
    private const val BACKWARD_DAYS = 45

    /** Immutable settings snapshot; its signature gates cache validity. */
    data class Settings(
        val enabledFestivals: Set<String>,
        val includeJieqi: Boolean
    ) {
        fun signature(): String =
            enabledFestivals.sorted().joinToString(",") + "|" + includeJieqi
    }

    /** Answer for one queried day. Null fields mean "not the festival itself". */
    class FestivalContext @JvmOverloads constructor(
        val julianDay: Int,
        /** Festival key when the day is a festival/jieqi day itself, else null. */
        val festivalKey: String? = null,
        /** Display name matching [festivalKey], else null. */
        val festivalName: String? = null
    )

    private class Snapshot(
        val settingsKey: String,
        val startJd: Int,
        val endJd: Int,
        val activeDays: IntArray,
        val markers: Map<Int, LunarHelper.Marker>
    )

    private var snapshot: Snapshot? = null

    /**
     * Returns the context for [julianDay], or null when the day is outside
     * every window. Rebuilds the index if settings changed or the day is not
     * covered yet.
     */
    @Synchronized
    fun findFestivalContext(
        julianDay: Int,
        todayJulianDay: Int,
        settings: Settings
    ): FestivalContext? {
        val snap = currentSnapshot(julianDay, todayJulianDay, settings)
        if (java.util.Arrays.binarySearch(snap.activeDays, julianDay) < 0) return null
        val marker = snap.markers[julianDay]
        return if (marker != null) {
            FestivalContext(julianDay, marker.key, marker.name)
        } else {
            FestivalContext(julianDay)
        }
    }

    /** Drops the index (settings or locale changed, memory pressure, tests). */
    @Synchronized
    fun invalidate() {
        snapshot = null
    }

    private fun currentSnapshot(
        julianDay: Int,
        todayJulianDay: Int,
        settings: Settings
    ): Snapshot {
        val existing = snapshot
        if (existing != null && existing.settingsKey == settings.signature() &&
            julianDay >= existing.startJd && julianDay <= existing.endJd
        ) {
            return existing
        }
        val rebuilt = rebuild(julianDay, todayJulianDay, settings)
        snapshot = rebuilt
        return rebuilt
    }

    private fun rebuild(anchorJd: Int, todayJd: Int, settings: Settings): Snapshot {
        val startJd = minOf(todayJd, anchorJd) - BACKWARD_DAYS
        val endJd = maxOf(todayJd, anchorJd) + FORWARD_DAYS
        val markers = LunarHelper.collectFestivalDays(startJd, endJd, settings)
        val active = sortedSetOf<Int>()
        for ((jd, marker) in markers) {
            val before = LunarHelper.daysBefore(marker.key)
            val after = LunarHelper.daysAfter(marker.key)
            for (d in jd - before..jd + after) {
                active.add(d)
            }
        }
        return Snapshot(
            settingsKey = settings.signature(),
            startJd = startJd,
            endJd = endJd,
            activeDays = active.toIntArray(),
            markers = markers
        )
    }
}
