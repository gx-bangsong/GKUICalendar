/*
 * Copyright (C) 2026 The GKUICalendar Project
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

package com.android.calendar;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The temporary time block shown in the day grid after the user taps an empty slot.
 *
 * <p>The block holds a start and an end, in minutes from local midnight of the selected day.
 * Drags snap to {@link #SNAP_MINUTES}, the end never falls to or before the start, and both ends
 * stay inside the day. Times are converted with a lenient calendar, so a day with 23 or 25 hours
 * (daylight saving time) still yields the real instants, and 24:00 is the next midnight.
 *
 * <p>This class does no drawing, no touch handling and no provider access. The day view reads
 * it to draw and to hit-test, and it passes the same millis to the event editor.
 */
final class DayTimeSlotPreview {

    /** Drag granularity and the shortest block. */
    static final int SNAP_MINUTES = 15;
    static final int MINUTES_PER_DAY = 24 * 60;

    private int mStartMinute;
    private int mEndMinute;

    private DayTimeSlotPreview(int startMinute, int endMinute) {
        mStartMinute = startMinute;
        mEndMinute = endMinute;
    }

    /**
     * Creates the block for a tap at {@code tapMinute}. The start is snapped down to the grid and
     * the block lasts {@code durationMinutes}, cut at the end of the day. Near midnight the start
     * moves back so the block keeps at least {@link #SNAP_MINUTES}.
     */
    static DayTimeSlotPreview forTap(int tapMinute, int durationMinutes) {
        int start = clamp(snap(tapMinute), 0, MINUTES_PER_DAY - SNAP_MINUTES);
        int duration = Math.max(durationMinutes, SNAP_MINUTES);
        int end = Math.min(start + duration, MINUTES_PER_DAY);
        if (end - start < SNAP_MINUTES) {
            start = end - SNAP_MINUTES;
        }
        return new DayTimeSlotPreview(start, end);
    }

    int getStartMinute() {
        return mStartMinute;
    }

    int getEndMinute() {
        return mEndMinute;
    }

    /** Moves the start to {@code minute}, snapped, but never past the end minus one step. */
    void dragStart(int minute) {
        mStartMinute = clamp(snap(minute), 0, mEndMinute - SNAP_MINUTES);
    }

    /** Moves the end to {@code minute}, snapped, but never before the start plus one step. */
    void dragEnd(int minute) {
        mEndMinute = clamp(snap(minute), mStartMinute + SNAP_MINUTES, MINUTES_PER_DAY);
    }

    /** Keyboard and accessibility steps: moves one end by a signed number of minutes. */
    void moveStartBy(int deltaMinutes) {
        dragStart(mStartMinute + deltaMinutes);
    }

    void moveEndBy(int deltaMinutes) {
        dragEnd(mEndMinute + deltaMinutes);
    }

    /**
     * Converts a vertical offset in the grid (pixels from the top of the hour-0 row) to a minute
     * of the day. {@code pixelsPerHour} is the height of one hour row including its gap.
     */
    static int minuteFromOffset(float offsetPixels, float pixelsPerHour) {
        if (pixelsPerHour <= 0f) {
            return 0;
        }
        return Math.round(offsetPixels * 60f / pixelsPerHour);
    }

    /** Local instant of the start on the given calendar day. */
    long startMillis(int year, int month, int dayOfMonth, TimeZone zone) {
        return millisAt(year, month, dayOfMonth, mStartMinute, zone);
    }

    /** Local instant of the end on the given calendar day. 24:00 is the next midnight. */
    long endMillis(int year, int month, int dayOfMonth, TimeZone zone) {
        return millisAt(year, month, dayOfMonth, mEndMinute, zone);
    }

    /**
     * The instant of {@code minute} on the given day. {@code month} is zero-based, as in
     * {@link Calendar}. Minutes past the day's end roll over into the next day through the
     * calendar, so DST days keep their real length.
     */
    static long millisAt(int year, int month, int dayOfMonth, int minute, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.clear();
        calendar.setLenient(true);
        calendar.set(year, month, dayOfMonth, minute / 60, minute % 60, 0);
        return calendar.getTimeInMillis();
    }

    /** Rounds to the nearest {@link #SNAP_MINUTES} step and keeps the result inside the day. */
    static int snap(int minute) {
        int rounded = Math.round(minute / (float) SNAP_MINUTES) * SNAP_MINUTES;
        return clamp(rounded, 0, MINUTES_PER_DAY);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
