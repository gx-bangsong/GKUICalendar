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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.util.TimeZone;

/**
 * Pure rules of the day-grid time block: snapping, drag limits, midnight and daylight saving
 * time. These are JVM tests of logic only. They do not show how the block is drawn or touched.
 */
public class DayTimeSlotPreviewTest {

    private static final int MIN_9_30 = 9 * 60 + 30;
    private static final int MIN_10_30 = 10 * 60 + 30;

    private static TimeZone zone(String id) {
        return TimeZone.getTimeZone(id);
    }

    private static long at(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    public void snapRoundsToTheNearestFifteenMinutes() {
        assertEquals(0, DayTimeSlotPreview.snap(0));
        assertEquals(0, DayTimeSlotPreview.snap(7));
        assertEquals(15, DayTimeSlotPreview.snap(8));
        assertEquals(45, DayTimeSlotPreview.snap(52));
        assertEquals(60, DayTimeSlotPreview.snap(53));
        assertEquals(1440, DayTimeSlotPreview.snap(1439));
        assertEquals(0, DayTimeSlotPreview.snap(-40));
    }

    @Test
    public void tapStartsAtTheSnappedMinuteAndLastsTheDefaultDuration() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(9 * 60 + 37, 60);
        assertEquals(MIN_9_30, block.getStartMinute());
        assertEquals(MIN_10_30, block.getEndMinute());
    }

    @Test
    public void tapNearMidnightKeepsAtLeastOneStepAndNeverRunsPastTheDay() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(23 * 60 + 50, 60);
        assertEquals(1425, block.getStartMinute());
        assertEquals(1440, block.getEndMinute());
        assertTrue(block.getEndMinute() - block.getStartMinute() >= DayTimeSlotPreview.SNAP_MINUTES);
    }

    @Test
    public void zeroOrNegativeDurationFallsBackToOneStep() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(600, 0);
        assertEquals(DayTimeSlotPreview.SNAP_MINUTES,
                block.getEndMinute() - block.getStartMinute());
    }

    @Test
    public void startDragNeverPassesTheEndMinusOneStep() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(9 * 60, 30);
        block.dragStart(20 * 60);
        assertEquals(block.getEndMinute() - DayTimeSlotPreview.SNAP_MINUTES,
                block.getStartMinute());
        assertTrue(block.getStartMinute() < block.getEndMinute());
    }

    @Test
    public void endDragNeverGoesBeforeTheStartPlusOneStep() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(9 * 60, 60);
        block.dragEnd(0);
        assertEquals(block.getStartMinute() + DayTimeSlotPreview.SNAP_MINUTES,
                block.getEndMinute());
    }

    @Test
    public void dragsStayInsideTheDay() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(9 * 60, 60);
        block.dragStart(-500);
        assertEquals(0, block.getStartMinute());
        block.dragEnd(5000);
        assertEquals(DayTimeSlotPreview.MINUTES_PER_DAY, block.getEndMinute());
    }

    @Test
    public void dragIsSnappedToTheGrid() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(9 * 60, 60);
        block.dragEnd(11 * 60 + 4);
        assertEquals(11 * 60, block.getEndMinute());
        block.dragStart(8 * 60 + 58);
        assertEquals(9 * 60, block.getStartMinute());
    }

    @Test
    public void accessibilityStepsMoveOneEndByOneStepAndStayValid() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(9 * 60, 30);
        block.moveEndBy(DayTimeSlotPreview.SNAP_MINUTES);
        assertEquals(9 * 60 + 45, block.getEndMinute());
        block.moveStartBy(-DayTimeSlotPreview.SNAP_MINUTES);
        assertEquals(8 * 60 + 45, block.getStartMinute());
        // Stepping the start past the end stops one step before the end
        for (int i = 0; i < 10; i++) {
            block.moveStartBy(DayTimeSlotPreview.SNAP_MINUTES);
        }
        assertEquals(block.getEndMinute() - DayTimeSlotPreview.SNAP_MINUTES,
                block.getStartMinute());
    }

    @Test
    public void offsetToMinuteUsesTheHourRowHeight() {
        // 64 px per hour: 32 px is half an hour, 16 px a quarter
        assertEquals(30, DayTimeSlotPreview.minuteFromOffset(32f, 64f));
        assertEquals(15, DayTimeSlotPreview.minuteFromOffset(16f, 64f));
        assertEquals(0, DayTimeSlotPreview.minuteFromOffset(10f, 0f));
    }

    @Test
    public void editorMillisAreTheExactStartAndEndOfTheBlock() {
        // The same calls feed the label, the drawing and the create-event request.
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(MIN_9_30, 60);
        TimeZone utc = zone("UTC");
        long start = block.startMillis(2026, 9, 22, utc);
        long end = block.endMillis(2026, 9, 22, utc);
        assertEquals(at("2026-10-22T09:30:00Z"), start);
        assertEquals(at("2026-10-22T10:30:00Z"), end);
    }

    @Test
    public void taipeiHasNoDaylightSavingSoTheBlockLastsItsDuration() {
        DayTimeSlotPreview block = DayTimeSlotPreview.forTap(MIN_9_30, 60);
        TimeZone taipei = zone("Asia/Taipei");
        long start = block.startMillis(2026, 9, 22, taipei);
        long end = block.endMillis(2026, 9, 22, taipei);
        assertEquals(60L * 60_000L, end - start);
        assertEquals(at("2026-10-22T01:30:00Z"), start);
    }

    @Test
    public void wholeDayOnASpringForwardDayIsTwentyThreeHours() {
        // New York, 2026-03-08: clocks go forward, so the day has 23 hours
        TimeZone ny = zone("America/New_York");
        long start = DayTimeSlotPreview.millisAt(2026, 2, 8, 0, ny);
        long end = DayTimeSlotPreview.millisAt(2026, 2, 8, DayTimeSlotPreview.MINUTES_PER_DAY, ny);
        assertEquals(23L * 3_600_000L, end - start);
        assertEquals(at("2026-03-09T04:00:00Z"), end);
    }

    @Test
    public void wholeDayOnAFallBackDayIsTwentyFiveHours() {
        // New York, 2026-11-01: clocks go back, so the day has 25 hours
        TimeZone ny = zone("America/New_York");
        long start = DayTimeSlotPreview.millisAt(2026, 10, 1, 0, ny);
        long end = DayTimeSlotPreview.millisAt(2026, 10, 1, DayTimeSlotPreview.MINUTES_PER_DAY, ny);
        assertEquals(25L * 3_600_000L, end - start);
    }

    @Test
    public void midnightEndIsTheStartOfTheNextDay() {
        long nextMidnight = DayTimeSlotPreview.millisAt(2026, 9, 22, 1440, zone("UTC"));
        assertEquals(DayTimeSlotPreview.millisAt(2026, 9, 23, 0, zone("UTC")), nextMidnight);
    }

    @Test
    public void aTimeInTheSpringGapResolvesWithoutFailing() {
        // 02:30 does not exist in New York on 2026-03-08; the calendar moves it forward
        long millis = DayTimeSlotPreview.millisAt(2026, 2, 8, 150, zone("America/New_York"));
        assertEquals(at("2026-03-08T07:30:00Z"), millis);
    }
}
