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

package com.android.calendar.timezonepicker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.calendar.timezonepicker.TimeZoneSearchMatcher.Entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

/** Pure JVM tests for the time zone search and offset rules. */
public class TimeZoneSearchMatcherTest {

    private static final long HOUR = 3_600_000L;
    private static final long MINUTE = 60_000L;

    private static List<Entry> zones() {
        return new ArrayList<>(Arrays.asList(
                new Entry("Asia/Shanghai", "China Standard Time", "China", (int) (8 * HOUR)),
                new Entry("America/New_York", "Eastern Time", "United States", (int) (-5 * HOUR)),
                new Entry("Asia/Kolkata", "India Standard Time", "India", (int) (5 * HOUR + 30 * MINUTE)),
                new Entry("Europe/Bucharest", "Eastern European Time", "Romania", (int) (2 * HOUR)),
                new Entry("Pacific/Chatham", "Chatham Time", "New Zealand", (int) (12 * HOUR + 45 * MINUTE))));
    }

    @Test
    public void emptyAndBlankQueriesMatchEverythingInOrder() {
        assertEquals(5, TimeZoneSearchMatcher.filter(zones(), "").size());
        assertEquals(5, TimeZoneSearchMatcher.filter(zones(), "   ").size());
        assertEquals(5, TimeZoneSearchMatcher.filter(zones(), null).size());
        assertEquals("Asia/Shanghai", TimeZoneSearchMatcher.filter(zones(), "").get(0).id);
    }

    @Test
    public void matchesCountryCaseInsensitivelyAndTrimsTheQuery() {
        List<Entry> result = TimeZoneSearchMatcher.filter(zones(), "  ROMANIA ");
        assertEquals(1, result.size());
        assertEquals("Europe/Bucharest", result.get(0).id);
    }

    @Test
    public void matchesZoneNameAndZoneIdLookup() {
        assertEquals("Asia/Kolkata",
                TimeZoneSearchMatcher.filter(zones(), "india standard").get(0).id);
        assertEquals("America/New_York",
                TimeZoneSearchMatcher.filter(zones(), "america/new_york").get(0).id);
    }

    @Test
    public void underscoreInIdMatchesSpace() {
        assertEquals("America/New_York",
                TimeZoneSearchMatcher.filter(zones(), "new york").get(0).id);
    }

    @Test
    public void noMatchReturnsEmptyList() {
        assertTrue(TimeZoneSearchMatcher.filter(zones(), "Atlantis").isEmpty());
    }

    @Test
    public void sameNameZonesAreNotMerged() {
        List<Entry> twins = new ArrayList<>(Arrays.asList(
                new Entry("Asia/Shanghai", "China Standard Time", "China", (int) (8 * HOUR)),
                new Entry("Asia/Taipei", "China Standard Time", "Taiwan", (int) (8 * HOUR))));
        List<Entry> result = TimeZoneSearchMatcher.filter(twins, "china standard");
        assertEquals(2, result.size());
        assertEquals("Asia/Taipei", result.get(1).id);
    }

    @Test
    public void formatsWholeFractionalAndNegativeOffsets() {
        assertEquals("GMT", TimeZoneSearchMatcher.formatOffset(0));
        assertEquals("GMT+08:00", TimeZoneSearchMatcher.formatOffset(8 * HOUR));
        assertEquals("GMT+05:30", TimeZoneSearchMatcher.formatOffset(5 * HOUR + 30 * MINUTE));
        assertEquals("GMT+12:45", TimeZoneSearchMatcher.formatOffset(12 * HOUR + 45 * MINUTE));
        assertEquals("GMT-05:00", TimeZoneSearchMatcher.formatOffset(-5 * HOUR));
        assertEquals("GMT-03:30", TimeZoneSearchMatcher.formatOffset(-(3 * HOUR + 30 * MINUTE)));
    }

    @Test
    public void offsetFollowsDaylightSavingAtTheReferenceDate() {
        long january = date(2026, 1, 15);
        long july = date(2026, 7, 15);
        // New York: standard time in January, daylight time in July.
        assertEquals(-5 * HOUR, TimeZoneSearchMatcher.offsetAt("America/New_York", january));
        assertEquals(-4 * HOUR, TimeZoneSearchMatcher.offsetAt("America/New_York", july));
        // Shanghai has no daylight saving, so the offset is the same on both dates.
        assertEquals(8 * HOUR, TimeZoneSearchMatcher.offsetAt("Asia/Shanghai", january));
        assertEquals(8 * HOUR, TimeZoneSearchMatcher.offsetAt("Asia/Shanghai", july));
    }

    @Test
    public void offsetOfOneZoneDependsOnTheReferenceTimeOfItsOwnCaller() {
        // The settings page uses "now", the event form uses the event start. The same zone
        // must show the offset of the time each caller passes in, not a shared value.
        long winter = date(2026, 1, 15);
        long summer = date(2026, 7, 15);
        assertEquals(1 * HOUR, TimeZoneSearchMatcher.offsetAt("Europe/Stockholm", winter));
        assertEquals(2 * HOUR, TimeZoneSearchMatcher.offsetAt("Europe/Stockholm", summer));
    }

    @Test
    public void unknownIdIsNotKnownEvenThoughJavaWouldReturnGmt() {
        assertFalse(TimeZoneSearchMatcher.isKnownId("Mars/Olympus_Mons"));
        assertFalse(TimeZoneSearchMatcher.isKnownId(""));
        assertFalse(TimeZoneSearchMatcher.isKnownId(null));
        assertTrue(TimeZoneSearchMatcher.isKnownId("Asia/Shanghai"));
    }

    private static long date(int year, int month, int day) {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month - 1, day, 12, 0, 0);
        return c.getTimeInMillis();
    }
}
