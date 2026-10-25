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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Time labels of the day and week views. The pure rules run on the JVM. Source guards keep the
 * drawing path free of formatting work and the old static label table out of the build. Neither
 * kind is visual acceptance: the 12/24-hour labels on a device are still pending.
 */
public class TimeLabelFormatTest {

    private static final String ZH_HOUR = "%1$s%2$d点";
    private static final String ZH_MINUTE = "%1$s%2$d:%3$02d";
    private static final String EN_HOUR = "%2$d %1$s";
    private static final String EN_MINUTE = "%2$d:%3$02d %1$s";

    private static TimeLabelFormat zh12() {
        return new TimeLabelFormat("zh-CN", false, "上午", "下午", ZH_HOUR, ZH_MINUTE);
    }

    private static TimeLabelFormat en12() {
        return new TimeLabelFormat("en-US", false, "AM", "PM", EN_HOUR, EN_MINUTE);
    }

    private static TimeLabelFormat zh24() {
        return new TimeLabelFormat("zh-CN", true, "上午", "下午", ZH_HOUR, ZH_MINUTE);
    }

    // ---------- Chinese 12-hour hour labels: period first, then the number and 点 ----------

    @Test
    public void chineseMorningAndAfternoonHours() {
        TimeLabelFormat f = zh12();
        assertEquals("上午9点", f.hourLabel(9));
        assertEquals("下午1点", f.hourLabel(13));
        assertEquals("下午11点", f.hourLabel(23));
    }

    @Test
    public void midnightIsMorningTwelveAndNoonIsAfternoonTwelve() {
        TimeLabelFormat f = zh12();
        assertEquals("上午12点", f.hourLabel(0));
        assertEquals("下午12点", f.hourLabel(12));
        assertEquals("上午1点", f.hourLabel(1));
    }

    @Test
    public void chineseNeverReplacesTheEnglishWords() {
        TimeLabelFormat f = zh12();
        for (int hour = 0; hour < 24; hour++) {
            String label = f.hourLabel(hour);
            assertFalse(label, label.contains("AM") || label.contains("PM"));
            assertTrue(label, label.endsWith("点"));
        }
    }

    // ---------- Chinese 12-hour exact times ----------

    @Test
    public void chinesePreciseTimesKeepTheMinuteAfterTheHour() {
        TimeLabelFormat f = zh12();
        assertEquals("上午9:15", f.preciseLabel(9 * 60 + 15));
        assertEquals("下午1:30", f.preciseLabel(13 * 60 + 30));
        assertEquals("上午12:00", f.preciseLabel(0));
        assertEquals("下午12:45", f.preciseLabel(12 * 60 + 45));
    }

    @Test
    public void endOfDayIsNeverAfternoonTwelve() {
        TimeLabelFormat f = zh12();
        String end = f.preciseLabel(TimeLabelFormat.MINUTES_PER_DAY);
        assertEquals("上午12:00", end);
        assertFalse(end.startsWith("下午"));
    }

    // ---------- 24-hour form: no period word at all ----------

    @Test
    public void twentyFourHourLabelsHaveNoPeriodEvenInChinese() {
        TimeLabelFormat f = zh24();
        assertEquals("09:00", f.hourLabel(9));
        assertEquals("00:00", f.hourLabel(0));
        assertEquals("13:00", f.hourLabel(13));
        assertEquals("09:15", f.preciseLabel(9 * 60 + 15));
        assertEquals("24:00", f.preciseLabel(TimeLabelFormat.MINUTES_PER_DAY));
        for (int hour = 0; hour < 24; hour++) {
            String label = f.hourLabel(hour);
            assertFalse(label, label.contains("上午") || label.contains("下午"));
        }
    }

    // ---------- English keeps its own order ----------

    @Test
    public void englishKeepsPeriodAfterTheNumber() {
        TimeLabelFormat f = en12();
        assertEquals("9 AM", f.hourLabel(9));
        assertEquals("1 PM", f.hourLabel(13));
        assertEquals("12 AM", f.hourLabel(0));
        assertEquals("12 PM", f.hourLabel(12));
        assertEquals("9:15 AM", f.preciseLabel(9 * 60 + 15));
    }

    // ---------- Cache identity and sample widths ----------

    @Test
    public void isForMatchesLocaleAndClockOnly() {
        TimeLabelFormat f = zh12();
        assertTrue(f.isFor("zh-CN", false));
        assertFalse("clock change must rebuild", f.isFor("zh-CN", true));
        assertFalse("locale change must rebuild", f.isFor("zh-TW", false));
        assertFalse(f.isFor("en-US", false));
    }

    @Test
    public void samplesCoverEveryHourAndTheEndOfDay() {
        String[] samples = zh12().sampleLabels();
        // 24 full hours, 24 quarter-hour 45 times, and the end of the day.
        assertEquals(49, samples.length);
        assertTrue(contains(samples, "上午9点"));
        assertTrue(contains(samples, "下午12:45"));
        assertTrue(contains(samples, "上午12:00"));
    }

    @Test
    public void hourLabelIndexWrapsSafely() {
        assertEquals("上午9点", zh12().hourLabel(9 + 24));
        assertEquals("上午9点", zh12().hourLabel(-15));
    }

    private static boolean contains(String[] values, String wanted) {
        for (String v : values) {
            if (wanted.equals(v)) {
                return true;
            }
        }
        return false;
    }

    // ---------- Source guards ----------

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    /** The text of one method: from its signature to the matching closing brace. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing " + signature, start >= 0);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError("unclosed " + signature);
    }

    @Test
    public void staticCalendarDataTableIsGone() {
        assertFalse(new File("src/main/java/com/android/calendar/CalendarData.java").exists());
    }

    @Test
    public void labelsAreBuiltOnResumeNotWhileDrawing() throws Exception {
        String dayView = read("src/main/java/com/android/calendar/DayView.java");
        String resume = method(dayView, "public void handleOnResume()");
        assertTrue("resume refreshes the labels", resume.contains("refreshTimeLabels()"));

        String refresh = method(dayView, "private void refreshTimeLabels()");
        assertTrue(refresh.contains("new TimeLabelFormat("));
        assertTrue(refresh.contains("R.string.time_label_hour_12h"));
        assertTrue(refresh.contains("R.string.time_label_minute_12h"));
        assertTrue("width is measured from real labels", dayView.contains("sampleLabels()"));

        for (String drawing : new String[] {
                "private void doDraw(Canvas canvas)",
                "private void drawHours(",
                "private void drawSlotTimeLabels(",
                "private void drawGridBackground(",
        }) {
            String body = method(dayView, drawing);
            assertFalse(drawing + " must not build formats", body.contains("new TimeLabelFormat"));
            assertFalse(drawing + " must not query the 12/24 setting",
                    body.contains("DateFormat.is24HourFormat"));
            assertFalse(drawing + " must not read the locale",
                    body.contains("getConfiguration()"));
        }
    }

    @Test
    public void shortLabelsDoNotUseTheOldEnglishWidthProbe() throws Exception {
        String dayView = read("src/main/java/com/android/calendar/DayView.java");
        assertFalse("the 12 AM / 22:00 probe is gone",
                dayView.contains("{\"12 AM\", \"12 PM\", \"22:00\"}"));
    }

    @Test
    public void formatterHasNoStringReplacementOrCaseChange() throws Exception {
        String source = read("src/main/java/com/android/calendar/TimeLabelFormat.java");
        assertFalse(source.contains("replace(\"AM\""));
        assertFalse(source.contains("replace(\"PM\""));
        assertFalse(source.contains("toUpperCase"));
        assertFalse(source.contains("toLowerCase"));
    }

    @Test
    public void resourcePatternsPutTheChinesePeriodFirst() throws Exception {
        String rCN = read("src/main/res/values-zh-rCN/strings.xml");
        String rTW = read("src/main/res/values-zh-rTW/strings.xml");
        String zh = read("src/main/res/values-zh/strings.xml");
        for (String xml : new String[] {rCN, rTW, zh}) {
            assertTrue(xml.contains("<string name=\"time_label_hour_12h\">%1$s%2$d"));
            assertTrue(xml.contains("<string name=\"time_label_minute_12h\">%1$s%2$d:%3$02d"));
        }
        assertTrue(rCN.contains("点</string>"));
        assertTrue(rTW.contains("點"));
    }
}
