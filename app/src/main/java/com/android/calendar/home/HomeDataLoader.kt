/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.calendar.Event
import com.android.calendar.Utils
import com.android.calendar.lunar.LunarHelper
import com.android.calendar.lunar.LunarMode
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Reads events and lunar labels on a background thread through the existing
 * Event.loadEvents path (Instances query, visible calendars, declined filter).
 * Results are delivered on the main thread, and superseded requests are dropped.
 */
class HomeDataLoader(context: Context) {

    /** Per-day markers for one month grid, keyed by Julian day. */
    class MonthData(
        val firstJulian: Int,
        val dayColors: Map<Int, List<Int>>,
        val dayCounts: Map<Int, Int>,
        val lunarText: Map<Int, String>
    )

    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HomeDataLoader").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val monthGate = RequestGate()
    private val dayGate = RequestGate()

    /** Loads markers for [days] consecutive cells starting at [firstJulian]. */
    fun loadMonth(firstJulian: Int, days: Int, callback: (MonthData) -> Unit) {
        val token = monthGate.begin()
        executor.execute {
            val events = ArrayList<Event>()
            Event.loadEvents(appContext, events, firstJulian, days, token, monthGate.counter)
            val data = if (monthGate.isCurrent(token)) buildMonthData(firstJulian, days, events) else null
            mainHandler.post {
                if (data != null && monthGate.isCurrent(token)) callback(data)
            }
        }
    }

    /** Loads events that overlap [julianDay], sorted with all-day events first. */
    fun loadDay(julianDay: Int, callback: (List<Event>) -> Unit) {
        val token = dayGate.begin()
        executor.execute {
            val events = ArrayList<Event>()
            Event.loadEvents(appContext, events, julianDay, 1, token, dayGate.counter)
            events.sortWith(Comparator { a, b ->
                when {
                    a.allDay != b.allDay -> if (a.allDay) -1 else 1
                    else -> a.startMillis.compareTo(b.startMillis)
                }
            })
            val result: List<Event>? = if (dayGate.isCurrent(token)) events else null
            mainHandler.post {
                if (result != null && dayGate.isCurrent(token)) callback(result)
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun buildMonthData(firstJulian: Int, days: Int, events: List<Event>): MonthData {
        val spans = events.map { DayMarkers.Span(it.startDay, it.endDay, it.color) }
        val markers = DayMarkers.build(firstJulian, days, spans)
        val lunar = HashMap<Int, String>()
        if (Utils.getLunarMode(appContext) != LunarMode.OFF) {
            for (day in firstJulian until firstJulian + days) {
                lunar[day] = LunarHelper.getLunarDayText(day, false)
            }
        }
        return MonthData(firstJulian, markers.colors, markers.counts, lunar)
    }

}
