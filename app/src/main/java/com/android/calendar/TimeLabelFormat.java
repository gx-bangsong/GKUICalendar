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

import java.util.Locale;

/**
 * Text of the hour labels and the precise time labels in the day and week views. It has no
 * Android types, so the rules can be checked by JVM tests.
 *
 * <p>The 12-hour patterns come from string resources, so the language can add its own wording
 * ("9点" in Chinese, "9 AM" in English). The AM and PM words come from the locale that the app
 * actually uses. The 24-hour labels never carry AM or PM.
 *
 * <p>Labels take minutes of the displayed day, so they already follow the display time zone.
 * Building an instance is cheap, but it is done when the view resumes, never while drawing.
 */
final class TimeLabelFormat {

    /** A block that ends at the next midnight has this minute value. */
    static final int MINUTES_PER_DAY = 1440;

    private final String mLocaleTag;
    private final boolean mIs24Hour;
    private final String mAm;
    private final String mPm;
    private final String mHourPattern12;
    private final String mMinutePattern12;
    private final String[] mHourLabels = new String[24];

    /**
     * @param localeTag the locale this format was built for, as from Locale.toLanguageTag()
     * @param is24Hour true for the 24-hour clock
     * @param am the AM word of the locale, for example "上午" or "AM"
     * @param pm the PM word of the locale, for example "下午" or "PM"
     * @param hourPattern12 12-hour hour label: %1$s = AM/PM word, %2$d = hour (1..12)
     * @param minutePattern12 12-hour exact time: %1$s = AM/PM word, %2$d = hour, %3$02d = minute
     */
    TimeLabelFormat(String localeTag, boolean is24Hour, String am, String pm,
            String hourPattern12, String minutePattern12) {
        mLocaleTag = localeTag;
        mIs24Hour = is24Hour;
        mAm = am;
        mPm = pm;
        mHourPattern12 = hourPattern12;
        mMinutePattern12 = minutePattern12;
        for (int hour = 0; hour < 24; hour++) {
            mHourLabels[hour] = buildHourLabel(hour);
        }
    }

    /** Whether this instance is still right for the given locale and clock setting. */
    boolean isFor(String localeTag, boolean is24Hour) {
        return mIs24Hour == is24Hour && mLocaleTag.equals(localeTag);
    }

    /** The label of a full hour, 0..23. Built once, so drawing only reads it. */
    String hourLabel(int hour) {
        return mHourLabels[((hour % 24) + 24) % 24];
    }

    /**
     * The exact time of a minute of the displayed day, for the preview's start and end labels. The
     * value 1440 is the end of the day. In 24-hour form it is "24:00". In 12-hour form it is the
     * midnight that ends the day, shown as the morning hour 12.
     */
    String preciseLabel(int minuteOfDay) {
        if (mIs24Hour && minuteOfDay >= MINUTES_PER_DAY) {
            return "24:00";
        }
        int minute = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        int hour = minute / 60;
        int min = minute % 60;
        if (mIs24Hour) {
            return String.format(Locale.ROOT, "%02d:%02d", hour, min);
        }
        return String.format(Locale.ROOT, mMinutePattern12, period(hour), twelve(hour), min);
    }

    /**
     * Every label that can be drawn at the time column, for measuring its width. This is each
     * full hour and the exact time at quarter-hour 45 of each hour, plus the end of the day.
     */
    String[] sampleLabels() {
        String[] samples = new String[24 + 24 + 1];
        int n = 0;
        for (int hour = 0; hour < 24; hour++) {
            samples[n++] = mHourLabels[hour];
        }
        for (int hour = 0; hour < 24; hour++) {
            samples[n++] = preciseLabel(hour * 60 + 45);
        }
        samples[n] = preciseLabel(MINUTES_PER_DAY);
        return samples;
    }

    private String buildHourLabel(int hour) {
        if (mIs24Hour) {
            return String.format(Locale.ROOT, "%02d:00", hour);
        }
        return String.format(Locale.ROOT, mHourPattern12, period(hour), twelve(hour));
    }

    /** The AM or PM word for an hour: hours 0..11 are AM, 12..23 are PM. */
    private String period(int hour) {
        return hour < 12 ? mAm : mPm;
    }

    /** The 12-hour number: 0 and 12 both show 12, so midnight is 上午12 and noon is 下午12. */
    private static int twelve(int hour) {
        int h = hour % 12;
        return h == 0 ? 12 : h;
    }
}
