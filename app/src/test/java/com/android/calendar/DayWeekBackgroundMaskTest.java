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

import com.android.calendar.settings.GeneralPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * The day and week background switch: its key, its own group in Display details, its default,
 * how DayView reads it on resume, and that the month views never read it. Static checks of the
 * source and the XML. They guard the wiring and are not visual acceptance.
 */
public class DayWeekBackgroundMaskTest {

    private static final String DAY_VIEW = "src/main/java/com/android/calendar/DayView.java";
    private static final String DAY_FRAGMENT = "src/main/java/com/android/calendar/DayFragment.java";
    private static final String DETAILS_XML = "src/main/res/xml/view_details_preferences.xml";
    private static final String STRINGS = "src/main/res/values/strings.xml";
    private static final String STRINGS_ZH = "src/main/res/values-zh-rCN/strings.xml";
    private static final String KEY = "pref_day_week_background_mask";

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    /** Text of one method, from its signature to the matching closing brace. */
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
        throw new AssertionError("unterminated " + signature);
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }

    @Test
    public void keyIsTheDedicatedDayWeekKeyAndDefaultsOff() throws Exception {
        assertEquals(KEY, GeneralPreferences.KEY_DAY_WEEK_BACKGROUND_MASK);
        String xml = read(DETAILS_XML);
        int at = xml.indexOf("app:key=\"" + KEY + "\"");
        assertTrue("switch row missing", at >= 0);
        String row = xml.substring(at, xml.indexOf('>', at) + 1);
        assertTrue(row.contains("app:defaultValue=\"false\""));
    }

    @Test
    public void switchHasItsOwnDayWeekGroupSeparateFromTheMonthGroups() throws Exception {
        String xml = read(DETAILS_XML);
        // Two month groups (portrait and landscape) plus the day and week group.
        assertEquals(3, countOf(xml, "<PreferenceCategory"));
        int groupStart = xml.lastIndexOf("<PreferenceCategory");
        int at = xml.indexOf("app:key=\"" + KEY + "\"");
        assertTrue("switch must be in the last group", at > groupStart);
        assertTrue(xml.substring(groupStart, at).contains("@string/preferences_day_week_view"));
        assertTrue(xml.substring(at).contains("@layout/preference_md3_switch"));
    }

    @Test
    public void switchSummaryDescribesWhatItControls() throws Exception {
        String en = read(STRINGS);
        String zh = read(STRINGS_ZH);
        assertTrue(en.contains("preferences_time_background_mask_summary"));
        assertTrue(zh.contains("preferences_time_background_mask_summary"));
        // The summary names the controlled parts, and does not say "past time" for them.
        int start = en.indexOf("preferences_time_background_mask_summary");
        String enSummary = en.substring(start, en.indexOf("</string>", start));
        assertTrue(enSummary.contains("time column"));
        assertFalse(enSummary.toLowerCase().contains("past"));
    }

    @Test
    public void dayViewReadsTheSwitchOnResumeNotPerFrame() throws Exception {
        String source = read(DAY_VIEW);
        String resume = method(source, "public void handleOnResume()");
        assertTrue(resume.contains("GeneralPreferences.isDayWeekBackgroundMaskEnabled(mContext)"));
        assertTrue(resume.contains("invalidate()"));
        assertFalse("the flag must not be static", source.contains("static boolean mBackgroundMaskOn"));
        String draw = method(source, "private void doDraw(Canvas canvas)");
        assertFalse("onDraw must not read preferences",
                draw.contains("GeneralPreferences"));
    }

    @Test
    public void currentAndAdjacentViewsBothRefreshOnFragmentResume() throws Exception {
        String fragment = read(DAY_FRAGMENT);
        String resume = method(fragment, "public void onResume()");
        assertTrue(resume.contains("mViewSwitcher.getCurrentView()"));
        assertTrue(resume.contains("mViewSwitcher.getNextView()"));
        assertEquals(2, countOf(resume, "handleOnResume()"));
    }

    @Test
    public void monthViewsNeverReadTheSwitch() throws Exception {
        File java = new File("src/main/java/com/android/calendar");
        File[] files = java.listFiles();
        assertTrue(files != null && files.length > 0);
        for (File f : files) {
            if (!f.isFile() || !f.getName().endsWith(".java")) {
                continue;
            }
            String name = f.getName();
            if (name.startsWith("Month") || name.contains("MonthAgenda")) {
                String text = read(f.getPath());
                assertFalse(name + " must not read the day/week switch",
                        text.contains("KEY_DAY_WEEK_BACKGROUND_MASK")
                                || text.contains("isDayWeekBackgroundMaskEnabled"));
            }
        }
    }

    @Test
    public void offMeansNoRoundedBlocksAndOnMeansRoundedBlocksOnlyInDay() {
        assertFalse(DayGridGeometry.drawsRoundedHourBlocks(1, false));
        assertTrue(DayGridGeometry.drawsRoundedHourBlocks(1, true));
        assertFalse(DayGridGeometry.drawsRoundedHourBlocks(7, true));
    }
}
