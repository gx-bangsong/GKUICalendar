package com.android.calendar.shift

import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.Context
import android.provider.CalendarContract
import com.android.calendar.AsyncQueryService
import com.android.calendar.Utils
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import java.util.ArrayList

object ShiftEventBuilder {
    private const val SHIFT_MARKER = "#ShiftScheduler"

    fun saveShifts(
        context: Context,
        calendarId: Long,
        preset: ShiftPreset,
        julianDays: Set<Int>,
        onComplete: () -> Unit
    ) {
        saveOperations(context, calendarId, julianDays.map { it to preset }, null, onComplete)
    }

    /** Saves a generated schedule, using native recurring CalendarContract events when possible. */
    fun saveSchedule(
        context: Context,
        calendarId: Long,
        shifts: Map<Int, ShiftPreset>,
        rule: ShiftRotationRule?,
        overrides: Map<Int, Long>,
        onComplete: () -> Unit
    ) {
        if (rule == null || overrides.isNotEmpty() || rule.patternPresetIds.isBlank()) {
            saveOperations(context, calendarId, shifts.map { it.key to it.value }, null, onComplete)
            return
        }

        val ids = rule.patternPresetIds.split(',').mapNotNull { it.toLongOrNull() }
        val operations = mutableListOf<ContentProviderOperation>()
        ids.forEachIndexed { slot, presetId ->
            if (presetId == 0L) return@forEachIndexed
            val preset = shifts.values.firstOrNull { it.id == presetId } ?: return@forEachIndexed
            val days = shifts.keys.filter { jd ->
                val diff = jd - rule.anchorJulianDay
                diff >= 0 && diff % ids.size == slot
            }.sorted()
            if (days.isNotEmpty()) {
                operations += buildInsert(context, calendarId, preset, days.first()).let { op ->
                    // Replace the one-shot insert with a native recurring event.
                    ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                        .withValues(opValues(context, calendarId, preset, days.first()))
                        .withValue(CalendarContract.Events.RRULE, "FREQ=DAILY;INTERVAL=${ids.size};COUNT=${days.size}")
                        .build()
                }
            }
        }
        executeBatch(context, operations, onComplete)
    }

    fun deleteSavedShifts(context: Context, calendarId: Long, onComplete: () -> Unit) {
        val service = object : AsyncQueryService(context) {
            override fun onDeleteComplete(token: Int, cookie: Any?, result: Int) = onComplete()
        }
        service.startDelete(
            0, null, CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf(calendarId.toString(), "%$SHIFT_MARKER%"), 0
        )
    }

    private fun saveOperations(
        context: Context,
        calendarId: Long,
        entries: List<Pair<Int, ShiftPreset>>,
        recurrence: String?,
        onComplete: () -> Unit
    ) {
        executeBatch(context, entries.map { (day, preset) ->
            buildInsert(context, calendarId, preset, day, recurrence)
        }, onComplete)
    }

    private fun buildInsert(context: Context, calendarId: Long, preset: ShiftPreset, julianDay: Int, recurrence: String? = null): ContentProviderOperation {
        return ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
            .withValues(opValues(context, calendarId, preset, julianDay))
            .apply { if (recurrence != null) withValue(CalendarContract.Events.RRULE, recurrence) }
            .build()
    }

    private fun opValues(context: Context, calendarId: Long, preset: ShiftPreset, julianDay: Int): android.content.ContentValues {
        val timeZone = Utils.getTimeZone(context, null)
        val start = getMillisFromJulianDay(julianDay, preset.startTime, timeZone)
        val end = getMillisFromJulianDay(julianDay, preset.endTime, timeZone)
        return android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, ShiftUtils.formatTitle(preset.title))
            put(CalendarContract.Events.DESCRIPTION, ShiftUtils.formatDescription(preset.alarmOffset, preset.ignoreHoliday) + "\n" + SHIFT_MARKER)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            put(CalendarContract.Events.EVENT_COLOR, preset.color)
        }
    }

    private fun executeBatch(context: Context, operations: List<ContentProviderOperation>, onComplete: () -> Unit) {
        if (operations.isEmpty()) { onComplete(); return }
        val service = object : AsyncQueryService(context) {
            override fun onBatchComplete(token: Int, cookie: Any?, results: Array<out ContentProviderResult>?) = onComplete()
        }
        service.startBatch(0, null, CalendarContract.AUTHORITY, ArrayList(operations), 0)
    }

    private fun getMillisFromJulianDay(julianDay: Int, minutesFromMidnight: Int, timeZone: String): Long {
        val time = Time(timeZone)
        time.setJulianDay(julianDay)
        time.hour = minutesFromMidnight / 60
        time.minute = minutesFromMidnight % 60
        time.second = 0
        return time.toMillis()
    }
}
