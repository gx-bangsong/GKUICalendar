/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.lunar

/**
 * Summary text for the festival multiple-choice row on the lunar settings page.
 *
 * Pure logic, so it can be unit-tested without Android. The stored value is not changed here.
 */
object LunarFestivalSummary {

    /**
     * @param selected the stored values (for example "spring"); may contain unknown values.
     * @param values the entry values, in display order.
     * @param labels the entry labels, in the same order as [values].
     * @param allText shown when every entry is selected.
     * @param noneText shown when no known entry is selected. An empty selection means no
     *        festival windows, so this text says so.
     * @param separator placed between the selected labels.
     */
    @JvmStatic
    fun summarize(
        selected: Set<String>,
        values: List<String>,
        labels: List<String>,
        allText: String,
        noneText: String,
        separator: String
    ): String {
        val count = minOf(values.size, labels.size)
        val chosen = (0 until count)
            .filter { selected.contains(values[it]) }
            .map { labels[it] }
        return when {
            count == 0 || chosen.isEmpty() -> noneText
            chosen.size == count -> allText
            else -> chosen.joinToString(separator)
        }
    }
}
