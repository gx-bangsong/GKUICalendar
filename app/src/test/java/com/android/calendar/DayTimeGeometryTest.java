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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Time geometry of the day and week grids: an event's position, its length, its hit-test
 * distance and its column share one coordinate system with the hour axis. The rules are EventGeometry's
 * own, checked here with a known scale. Nothing in EventGeometry is changed by these tests.
 *
 * Scale: 60 px per hour (1 px per minute) unless a test says otherwise, so the numbers read as
 * minutes. The hour gap is 1 px, as in DayView. Each hour slot is therefore 61 px tall.
 */
public class DayTimeGeometryTest {

    private static final int DAY = 2460000;
    private static final float GAP = 1f;
    private static final float MIN_HEIGHT = 10f;
    private static final float TOLERANCE = 2f;

    private static EventGeometry geometry(float hourHeight) {
        EventGeometry g = new EventGeometry();
        g.setHourGap(GAP);
        g.setMinEventHeight(MIN_HEIGHT);
        g.setHourHeight(hourHeight);
        return g;
    }

    private static Event timed(int startDay, int startMinute, int endDay, int endMinute) {
        Event e = new Event();
        e.allDay = false;
        e.startDay = startDay;
        e.endDay = endDay;
        e.startTime = startMinute;
        e.endTime = endMinute;
        // A lone event takes the whole column width. Without this the column maths divides by 0.
        e.setColumn(0);
        e.setMaxColumns(1);
        return e;
    }

    private static Event sameDay(int startMinute, int endMinute) {
        return timed(DAY, startMinute, DAY, endMinute);
    }

    @Test
    public void nineAmEventStartsOnTheNineAmAxis() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(9 * 60, 10 * 60);
        assertTrue(g.computeEventRect(DAY, 0, 0, 100, e));
        // The hour axis for 9:00 is the slot top of hour 9 (inset 0 here).
        assertEquals(DayGridGeometry.hourBlockTop(9, 61f, 0f), e.top, TOLERANCE);
    }

    @Test
    public void oneHourEventIsOneHourLong() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(9 * 60, 10 * 60);
        g.computeEventRect(DAY, 0, 0, 100, e);
        assertEquals(60f, e.bottom - e.top, TOLERANCE);
    }

    @Test
    public void fifteenMinutePreviewIsFifteenMinutes() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(9 * 60, 9 * 60 + 15);
        g.computeEventRect(DAY, 0, 0, 100, e);
        assertEquals(15f, e.bottom - e.top, TOLERANCE);
    }

    @Test
    public void multiHourEventIsOneContinuousBlockAcrossHourBoundaries() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(10 * 60 + 30, 13 * 60 + 15);
        assertTrue(g.computeEventRect(DAY, 0, 0, 100, e));
        float eleven = DayGridGeometry.hourBlockTop(11, 61f, 0f);
        // One rectangle: the 11:00 and 12:00 boundaries fall inside it.
        assertTrue(e.top < eleven && eleven < e.bottom);
        // 2h45m of time, plus the 1 px gap for each hour boundary it crosses.
        assertTrue(e.bottom - e.top >= 165f);
        assertTrue(e.bottom - e.top <= 165f + 4f);
    }

    @Test
    public void shortEventIsNeverShorterThanTheMinimumHeight() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(9 * 60, 9 * 60 + 5);
        g.computeEventRect(DAY, 0, 0, 100, e);
        assertEquals(MIN_HEIGHT, e.bottom - e.top, 0.01f);
    }

    @Test
    public void allDayEventIsNotPlacedOnTheTimeGrid() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(0, 0);
        e.allDay = true;
        assertFalse(g.computeEventRect(DAY, 0, 0, 100, e));
    }

    @Test
    public void multiDayEventSpanIsNotStretchedByTheHourHeight() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(0, 0);
        e.startMillis = 0L;
        e.endMillis = 2L * 24 * 60 * 60 * 1000;
        assertFalse(g.computeEventRect(DAY, 0, 0, 100, e));
    }

    @Test
    public void overlappingEventsShareTheWidthSideBySide() {
        EventGeometry g = geometry(60f);
        Event first = sameDay(9 * 60, 10 * 60);
        Event second = sameDay(9 * 60 + 30, 10 * 60 + 30);
        first.setColumn(0);
        first.setMaxColumns(2);
        second.setColumn(1);
        second.setMaxColumns(2);
        g.computeEventRect(DAY, 0, 0, 100, first);
        g.computeEventRect(DAY, 0, 0, 100, second);
        assertEquals(0f, first.left, 0.01f);
        assertEquals(50f, first.right, 0.01f);
        assertEquals(50f, second.left, 0.01f);
        assertEquals(100f, second.right, 0.01f);
        // They overlap in time, but not in x, so each stays tappable.
        assertTrue(first.right <= second.left);
    }

    @Test
    public void eventStartedYesterdayStartsAtTheTopOfTheDay() {
        EventGeometry g = geometry(60f);
        Event e = timed(DAY - 1, 22 * 60, DAY, 60);
        assertTrue(g.computeEventRect(DAY, 0, 0, 100, e));
        assertEquals(0f, e.top, 0.01f);
        assertEquals(60f, e.bottom, TOLERANCE);
    }

    @Test
    public void eventEndingTomorrowRunsToTheBottomOfTheDay() {
        EventGeometry g = geometry(60f);
        Event e = timed(DAY - 1, 22 * 60, DAY + 1, 60);
        assertTrue(g.computeEventRect(DAY, 0, 0, 100, e));
        assertEquals(0f, e.top, 0.01f);
        // The whole day is 1440 px, plus the gaps of the 23 hour boundaries.
        assertTrue(e.bottom >= 1440f);
        assertTrue(e.bottom <= 1440f + 23f);
    }

    @Test
    public void tapInsideTheBlockHitsAndTheDistanceOutsideMatchesTheEdge() {
        EventGeometry g = geometry(60f);
        Event e = sameDay(9 * 60, 10 * 60);
        g.computeEventRect(DAY, 0, 0, 100, e);
        // The 9:00 axis line is inside the block, so a tap there hits it.
        assertEquals(0f, g.pointToEvent(50f, e.top, e), 0.01f);
        // Below the bottom edge by 10 px, and above the top edge by 20 px.
        assertEquals(10f, g.pointToEvent(50f, e.bottom + 10f, e), 0.01f);
        assertEquals(20f, g.pointToEvent(50f, e.top - 20f, e), 0.01f);
    }

    @Test
    public void scrollMovesEveryEventByTheSameOffset() {
        EventGeometry g = geometry(60f);
        Event at0 = sameDay(9 * 60, 10 * 60);
        Event scrolled = sameDay(9 * 60, 10 * 60);
        g.computeEventRect(DAY, 0, 0, 100, at0);
        g.computeEventRect(DAY, 0, -300, 100, scrolled);
        assertEquals(at0.top - 300f, scrolled.top, 0.01f);
        assertEquals(at0.bottom - 300f, scrolled.bottom, 0.01f);
    }

    @Test
    public void windowResizeOrZoomKeepsEventsOnTheHourAxis() {
        // Two pixels per minute: the hour height changed, the grid and the event must agree.
        EventGeometry g = geometry(120f);
        float slot = 121f;
        Event e = sameDay(9 * 60, 10 * 60);
        g.computeEventRect(DAY, 0, 0, 100, e);
        assertEquals(DayGridGeometry.hourBlockTop(9, slot, 0f), e.top, TOLERANCE);
        assertEquals(120f, e.bottom - e.top, TOLERANCE);
    }

    @Test
    public void zoomedFifteenMinutePreviewStillMeasuresFifteenMinutes() {
        EventGeometry g = geometry(120f);
        Event e = sameDay(9 * 60, 9 * 60 + 15);
        g.computeEventRect(DAY, 0, 0, 100, e);
        assertEquals(30f, e.bottom - e.top, TOLERANCE);
    }
}
