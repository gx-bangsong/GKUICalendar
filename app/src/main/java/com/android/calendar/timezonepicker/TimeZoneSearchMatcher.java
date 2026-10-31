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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Pure search and display rules for the full-screen time zone page. No Android dependency, so the
 * rules can be covered by JVM unit tests.
 *
 * <p>Identity is always the zone ID. The display name, country and offset are only used to find
 * and describe a zone; they are never used to decide which zone is selected.
 */
public final class TimeZoneSearchMatcher {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    private TimeZoneSearchMatcher() {
    }

    /** One zone from {@link TimeZoneData}, with its offset at the reference time. */
    public static final class Entry {
        public final String id;
        public final String displayName;
        public final String country;
        /** Offset from UTC in milliseconds at the reference time, DST included. */
        public final int offsetMillis;

        public Entry(String id, String displayName, String country, int offsetMillis) {
            this.id = id;
            this.displayName = displayName;
            this.country = country;
            this.offsetMillis = offsetMillis;
        }
    }

    /**
     * Returns the entries that match the query. An empty or blank query matches everything.
     * Matching is case-insensitive over the display name, the country, and the zone ID. Underscores
     * in the ID also match spaces, so "new york" finds "America/New_York". Order is kept and
     * entries are never merged, so two zones with the same name stay separate.
     */
    public static List<Entry> filter(List<Entry> entries, String query) {
        List<Entry> result = new ArrayList<>();
        String needle = normalize(query);
        for (Entry entry : entries) {
            if (needle.isEmpty() || matches(entry, needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Trims and lower-cases a query. Used once per query, not once per entry. */
    public static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(Entry entry, String normalizedNeedle) {
        return contains(entry.displayName, normalizedNeedle)
                || contains(entry.country, normalizedNeedle)
                || contains(entry.id, normalizedNeedle)
                || (entry.id != null
                        && entry.id.replace('_', ' ').toLowerCase(Locale.ROOT)
                                .contains(normalizedNeedle));
    }

    private static boolean contains(String text, String normalizedNeedle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(normalizedNeedle);
    }

    /**
     * The offset of a zone at a reference instant. The reference instant decides whether daylight
     * saving time applies, so the same zone can show two different offsets on two dates.
     */
    public static int offsetAt(String zoneId, long referenceMillis) {
        return TimeZone.getTimeZone(zoneId).getOffset(referenceMillis);
    }

    /**
     * True only for IDs the platform knows. {@link TimeZone#getTimeZone(String)} silently returns
     * GMT for unknown IDs, so callers must check this first.
     */
    public static boolean isKnownId(String zoneId) {
        if (zoneId == null || zoneId.isEmpty()) {
            return false;
        }
        Set<String> known = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
        return known.contains(zoneId);
    }

    /**
     * Formats an offset as "GMT+05:30", "GMT-03:00", or "GMT" for zero. Only a note about the
     * offset; it is never used as the identity of a zone.
     */
    public static String formatOffset(long offsetMillis) {
        long minutes = Math.round(offsetMillis / (double) MILLIS_PER_MINUTE);
        if (minutes == 0) {
            return "GMT";
        }
        char sign = minutes < 0 ? '-' : '+';
        long absolute = Math.abs(minutes);
        return String.format(Locale.ROOT, "GMT%c%02d:%02d", sign, absolute / 60, absolute % 60);
    }
}
