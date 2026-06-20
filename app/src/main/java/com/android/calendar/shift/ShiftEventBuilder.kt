package com.android.calendar.shift

import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.Context
import android.provider.CalendarContract
import com.android.calendar.AsyncQueryService
import com.android.calendar.Utils
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.shift.db.ShiftPreset
import java.util.*

object ShiftEventBuilder {

    fun saveShifts(
        context: Context,
        calendarId: Long,
        preset: ShiftPreset,
        julianDays: Set<Int>,
        onComplete: () -> Unit
    ) {
        val ops = ArrayList<ContentProviderOperation>()
        val timeZone = Utils.getTimeZone(context, null)

        val finalTitle = ShiftUtils.formatTitle(preset.title)
        val description = ShiftUtils.formatDescription(preset.alarmOffset, preset.ignoreHoliday)

        for (julianDay in julianDays) {
            val startMillis = getMillisFromJulianDay(julianDay, preset.startTime, timeZone)
            val endMillis = getMillisFromJulianDay(julianDay, preset.endTime, timeZone)

            val op = ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                .withValue(CalendarContract.Events.TITLE, finalTitle)
                .withValue(CalendarContract.Events.DESCRIPTION, description)
                .withValue(CalendarContract.Events.DTSTART, startMillis)
                .withValue(CalendarContract.Events.DTEND, endMillis)
                .withValue(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                .withValue(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                .withValue(CalendarContract.Events.EVENT_COLOR, preset.color)
                .build()
            ops.add(op)
        }

        val queryService = object : AsyncQueryService(context) {
            override fun onBatchComplete(token: Int, cookie: Any?, results: Array<out ContentProviderResult>?) {
                onComplete()
            }
        }
        queryService.startBatch(0, null, CalendarContract.AUTHORITY, ops, 0)
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
