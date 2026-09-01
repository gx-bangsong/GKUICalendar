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

import android.util.SparseArray
import java.util.ArrayDeque

/**
 * Tiny LRU cache of [LunarInfo], keyed by julian day (a plain int - no
 * boxing). Capped at [MAX_ENTRIES]; entries hold only the four
 * already-materialized render fields, never full
 * [com.nlf.calendar.Lunar] objects.
 */
object LunarCache {

    private const val MAX_ENTRIES = 128

    private val cache = SparseArray<LunarInfo>(64)

    /** Access order, least recently used first. */
    private val order = ArrayDeque<Int>()

    private var settingsKey: String? = null

    @Synchronized
    fun get(julianDay: Int): LunarInfo? {
        val info = cache.get(julianDay) ?: return null
        touch(julianDay)
        return info
    }

    @Synchronized
    fun put(julianDay: Int, info: LunarInfo) {
        if (cache.indexOfKey(julianDay) >= 0) {
            cache.put(julianDay, info)
            touch(julianDay)
            return
        }
        if (cache.size() >= MAX_ENTRIES) {
            val eldest = order.pollFirst()
            if (eldest != null) cache.remove(eldest)
        }
        cache.put(julianDay, info)
        order.addLast(julianDay)
    }

    /** Entries are derived from settings; drop them when the settings change. */
    @Synchronized
    fun onSettingsChanged(key: String) {
        if (settingsKey != key) {
            settingsKey = key
            evictAll()
        }
    }

    @Synchronized
    fun evictAll() {
        cache.clear()
        order.clear()
    }

    @Synchronized
    fun size(): Int = cache.size()

    private fun touch(julianDay: Int) {
        if (order.peekLast() == julianDay) return
        order.remove(julianDay)
        order.addLast(julianDay)
    }
}
