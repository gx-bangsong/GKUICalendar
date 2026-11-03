/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

/**
 * Turns instance spans into per-day counts and colors for the month grid.
 * Spans are inclusive Julian days taken from the Instances rows, so all-day,
 * multi-day and recurring instances are counted on each day they cover.
 * This file must not reference Android types.
 */
object DayMarkers {

    /** One instance. [startDay]..[endDay] are inclusive Julian days. */
    data class Span(val startDay: Int, val endDay: Int, val color: Int)

    class Result(
        val counts: Map<Int, Int>,
        val colors: Map<Int, List<Int>>
    )

    @JvmStatic
    fun build(
        firstJulian: Int,
        days: Int,
        spans: List<Span>,
        maxColors: Int = MAX_COLORS_PER_DAY
    ): Result {
        val lastJulian = firstJulian + days - 1
        val counts = HashMap<Int, Int>()
        val colors = HashMap<Int, MutableList<Int>>()
        for (span in spans) {
            val from = maxOf(span.startDay, firstJulian)
            val to = minOf(span.endDay, lastJulian)
            var day = from
            while (day <= to) {
                counts[day] = (counts[day] ?: 0) + 1
                val list = colors.getOrPut(day) { ArrayList() }
                if (list.size < maxColors && !list.contains(span.color)) {
                    list.add(span.color)
                }
                day++
            }
        }
        return Result(counts, colors)
    }

    const val MAX_COLORS_PER_DAY = 3
}
