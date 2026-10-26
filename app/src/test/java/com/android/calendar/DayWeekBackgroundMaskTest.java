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
import java.util.regex.Pattern;

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
    public void dayDrawsRoundedHourBlocks() {
        assertTrue(DayGridGeometry.drawsRoundedHourBlocks(1));
    }

    @Test
    public void weekDrawsRoundedHourBlocksPerDayColumnToo() {
        // Week is a multi-day grid: every day column gets its own blocks, one per hour.
        for (int days = 2; days <= 7; days++) {
            assertTrue("days " + days, DayGridGeometry.drawsRoundedHourBlocks(days));
        }
        assertFalse(DayGridGeometry.drawsRoundedHourBlocks(0));
    }

    @Test
    public void hourBlocksDoNotDependOnTheMaskSwitch() throws Exception {
        // The blocks are the base layer. They are called at method level in doDraw, not inside
        // the mask's if block, and the block code never reads the mask.
        String source = read(DAY_VIEW);
        String grid = method(source, "private void drawGridBackground(");
        assertFalse("hour blocks must not depend on the mask", grid.contains("mBackgroundMaskOn"));
        String draw = method(source, "private void doDraw(Canvas canvas)");
        assertTrue(draw.contains("\n        drawGridBackground(r, canvas, p);"));
        assertFalse(draw.contains("if (mBackgroundMaskOn) {\n            drawGridBackground"));
    }

    @Test
    public void backgroundIsOneRoundRectPathWithNoThinLineFallback() throws Exception {
        String source = read(DAY_VIEW);
        assertFalse("the thin week lines are gone", source.contains("drawWeekGridLines"));
        String grid = method(source, "private void drawGridBackground(");
        assertEquals(1, countOf(grid, "drawRoundRect("));
        assertFalse(grid.contains("drawLines("));
    }

    @Test
    public void backgroundDrawsPerColumnInsideItsOwnDayEdges() throws Exception {
        // Each block is limited to its day column (edge plus the column inset), and the vertical
        // positions come from the same helpers the events and the time labels use.
        String rect = method(read(DAY_VIEW), "private float fillHourBlockRect(");
        assertTrue(rect.contains("computeDayLeftPosition(day) + HOUR_BLOCK_COLUMN_INSET"));
        assertTrue(rect.contains("computeDayLeftPosition(day + 1) - HOUR_BLOCK_COLUMN_INSET"));
        assertTrue(rect.contains("DayGridGeometry.hourBlockTop(hour, slotHeight, HOUR_BLOCK_INSET)"));
        assertTrue(rect.contains("DayGridGeometry.hourBlockBottom(hour, slotHeight, HOUR_BLOCK_INSET)"));
    }

    @Test
    public void backgroundNeverWritesStateOrChangesHitGeometry() throws Exception {
        // The background only reads its inputs and draws. It assigns no field, so time-to-Y
        // mapping, column edges, event layout, touch hit testing and preview length are unchanged.
        String grid = method(read(DAY_VIEW), "private void drawGridBackground(");
        assertFalse("no field assignment in the background",
                Pattern.compile("\\bm[A-Z]\\w*\\s*=[^=]").matcher(grid).find());
        assertFalse(grid.contains("remeasure("));
        assertFalse(grid.contains("setCellHeight"));
    }

    @Test
    public void grayMaskIsASeparateLayerFromTheHourBlocks() throws Exception {
        // Source guard: the grid background (the rounded hour blocks) does not read the mask.
        // The mask is drawn by drawBgColors, a separate call in doDraw.
        String source = read(DAY_VIEW);
        String grid = method(source, "private void drawGridBackground(");
        assertFalse("hour blocks must not depend on the mask", grid.contains("mBackgroundMaskOn"));
        String draw = method(source, "private void doDraw(Canvas canvas)");
        int mask = draw.indexOf("drawBgColors(r, canvas, p)");
        int blocks = draw.indexOf("drawGridBackground(r, canvas, p)");
        assertTrue("mask is drawn before the blocks", mask >= 0 && mask < blocks);
    }

    @Test
    public void maskIsHourBlockShapedAndNotAWholeRectangle() throws Exception {
        // The future mask is painted per hour block, with the same radius and rectangle as the
        // grid. The old right-angled rectangle is gone from the label-column path.
        String source = read(DAY_VIEW);
        String bg = method(source, "private void drawBgColors(");
        assertFalse("no whole-column rectangle for the mask", bg.contains("mFutureBgColor"));
        String mask = method(source, "private void drawFutureHourBlocks(");
        assertTrue(mask.contains("fillHourBlockRect(day, hour, slotHeight)"));
        assertTrue(mask.contains("DayGridGeometry.futureStartY(day, todayIndex, nowTop)"));
        assertTrue(mask.contains("canvas.drawRoundRect(mRectF, radius, radius, p)"));
        assertFalse("the mask must not paint a rectangle", mask.contains("drawRect("));
    }

    @Test
    public void maskAndBlocksShareOneRectangleHelper() throws Exception {
        // The column edge arithmetic exists once; the grid and the mask both call it.
        String source = read(DAY_VIEW);
        assertEquals(1, countOf(source, "computeDayLeftPosition(day) + HOUR_BLOCK_COLUMN_INSET"));
        assertTrue(method(source, "private void drawGridBackground(")
                .contains("fillHourBlockRect(day, hour, slotHeight)"));
    }

    @Test
    public void maskClipAndPaintStateAreRestored() throws Exception {
        // A block cut by the current time is clipped inside save/restore, and the paint colour
        // and style are put back. Nothing is left on the shared Paint.
        String mask = method(read(DAY_VIEW), "private void drawFutureHourBlocks(");
        assertEquals(1, countOf(mask, "canvas.save()"));
        assertEquals(1, countOf(mask, "canvas.restore()"));
        assertTrue(mask.contains("canvas.clipRect("));
        assertTrue(mask.contains("p.setColor(savedColor)"));
        assertTrue(mask.contains("p.setStyle(savedStyle)"));
    }

    @Test
    public void maskOnlyDrawsWhenTheSwitchIsOn() throws Exception {
        String draw = method(read(DAY_VIEW), "private void doDraw(Canvas canvas)");
        int gate = draw.indexOf("if (mBackgroundMaskOn)");
        int call = draw.indexOf("drawBgColors(r, canvas, p)");
        assertTrue(gate >= 0 && gate < call);
    }

    @Test
    public void summaryDoesNotClaimRoundedBlocksAnyMore() throws Exception {
        String en = read(STRINGS);
        int start = en.indexOf("preferences_time_background_mask_summary");
        String enSummary = en.substring(start, en.indexOf("</string>", start));
        assertFalse(enSummary.toLowerCase().contains("rounded"));
    }
}
