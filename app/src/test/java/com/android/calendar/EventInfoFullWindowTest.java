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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Source guards for the standalone event detail on large screens. These check the wiring only;
 * the reading layout on a device is verified separately.
 */
public class EventInfoFullWindowTest {

    private static final String FRAGMENT = "src/main/java/com/android/calendar/EventInfoFragment.java";
    private static final String DAY_VIEW = "src/main/java/com/android/calendar/DayView.java";
    private static final String LAYOUT = "src/main/res/layout/event_info.xml";
    private static final String COLORS = "src/main/res/values/colors.xml";

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void largeScreenQualifiersOpenStandaloneDetailFullWindow() throws Exception {
        String[] qualifiers = {
            "values-w600dp-h879dp", "values-w800dp-h1232dp", "values-w960dp-h527dp"
        };
        for (String q : qualifiers) {
            String config = read("src/main/res/" + q + "/config.xml");
            assertTrue(q, config.contains("<bool name=\"show_event_info_full_screen\">true</bool>"));
        }
    }

    @Test
    public void agendaSidePaneFlagIsNotChangedByTheDetailFix() throws Exception {
        String config = read("src/main/res/values-w600dp-h879dp/config.xml");
        assertTrue(config.contains("<bool name=\"agenda_show_event_info_full_screen\">false</bool>"));
    }

    @Test
    public void onlyTabletPanesCloseAfterDelete() throws Exception {
        String source = read(FRAGMENT);
        assertTrue(source.contains(
                "return mIsTabletConfig && mWindowStyle != FULL_WINDOW_STYLE;"));
        assertFalse("no direct tablet check left on the close paths",
                source.contains("!mIsTabletConfig"));
    }

    @Test
    public void shortFullWindowContentStillFillsTheViewport() throws Exception {
        String layout = read(LAYOUT);
        assertTrue(layout.contains("android:fillViewport=\"true\""));
        String source = read(FRAGMENT);
        assertTrue(source.contains("fillPanelToViewport()"));
        assertTrue(source.contains("panel.setMinimumHeight(min)"));
    }

    @Test
    public void nowLineUsesTheThemePrimaryNotAFixedRed() throws Exception {
        String dayView = read(DAY_VIEW);
        assertFalse(dayView.contains("R.color.now_line_color"));
        assertTrue(dayView.contains("mNowLineColor = com.google.android.material.color.MaterialColors"));
        assertFalse(read(COLORS).contains("now_line_color"));
    }
}
