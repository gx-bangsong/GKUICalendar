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

package com.android.calendar.agenda;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Entry-to-layout wiring of the standalone agenda, and the card row's structure. Static checks:
 * they show the card style is handed down on every path, and that the card rows have no old colour
 * dot, timeline or side bar. They are not visual acceptance.
 */
public class AgendaCardWiringTest {

    private static final String AGENDA_FRAGMENT = "src/main/java/com/android/calendar/agenda/AgendaFragment.java";
    private static final String WINDOW = "src/main/java/com/android/calendar/agenda/AgendaWindowAdapter.java";
    private static final String AGENDA_ADAPTER = "src/main/java/com/android/calendar/agenda/AgendaAdapter.java";
    private static final String BY_DAY = "src/main/java/com/android/calendar/agenda/AgendaByDayAdapter.java";
    private static final String ALL_IN_ONE = "src/main/java/com/android/calendar/AllInOneActivity.java";
    private static final String SEARCH = "src/main/java/com/android/calendar/SearchActivity.java";
    private static final String CARD_PHONE = "src/main/res/layout/agenda_item_card.xml";
    private static final String CARD_TABLET = "src/main/res/layout-sw600dp/agenda_item_card.xml";

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

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void restoredCardStyleIsReadBeforeTheListIsSetUp() throws Exception {
        String source = read(AGENDA_FRAGMENT);
        int restore = source.indexOf("mCardStyle = icicle.getBoolean(BUNDLE_KEY_CARD_STYLE");
        int handOff = source.indexOf("mAgendaListView.setCardStyle(mCardStyle)");
        assertTrue(restore > 0);
        assertTrue(handOff > 0);
        assertTrue("restore must happen in onCreate, before onCreateView hands the style down",
                restore < handOff);
        assertTrue(source.contains("outState.putBoolean(BUNDLE_KEY_CARD_STYLE, mCardStyle)"));
    }

    @Test
    public void entryPointsChooseTheirStyleExplicitly() throws Exception {
        String allInOne = read(ALL_IN_ONE);
        assertTrue(allInOne.contains("new AgendaFragment(timeMillis, false, true)"));
        assertTrue("month panel keeps the classic rows",
                allInOne.contains("new AgendaFragment(timeMillis, false);"));
        assertTrue("search keeps the classic rows",
                read(SEARCH).contains("new AgendaFragment(timeMillis, true)"));
    }

    @Test
    public void existingDayAdaptersAreRebuiltWhenTheStyleChanges() throws Exception {
        String window = read(WINDOW);
        int at = window.indexOf("public void setCardStyle(boolean cardStyle)");
        assertTrue(at > 0);
        String body = window.substring(at, window.indexOf("notifyDataSetChanged", at));
        assertTrue(body.contains("if (cardStyle == mCardStyle)"));
        assertTrue(body.contains("info.dayAdapter = new AgendaByDayAdapter(mContext, cardStyle)"));
    }

    @Test
    public void cardRowsDoNotPaintTheRowBackgroundOverTheCard() throws Exception {
        String byDay = read(BY_DAY);
        assertTrue(byDay.contains("private final boolean mCardStyle;"));
        int guards = 0;
        int index = 0;
        while ((index = byDay.indexOf("if (!mCardStyle) {", index)) >= 0) {
            guards++;
            index++;
        }
        assertEquals(2, guards);
    }

    @Test
    public void cardRowsHaveNoOldColourDotTimelineOrSideBar() throws Exception {
        for (String path : new String[] {CARD_PHONE, CARD_TABLET}) {
            String xml = read(path);
            assertFalse(path, xml.contains("ColorChipView"));
            assertFalse(path, xml.contains("agenda_item_color"));
            assertFalse(path, xml.contains("agenda_timeline_line"));
        }
    }

    @Test
    public void cardDateColumnPutsTheWeekdayAboveTheNumber() throws Exception {
        for (String path : new String[] {CARD_PHONE, CARD_TABLET}) {
            String xml = read(path);
            int day = xml.indexOf("@+id/agenda_event_day");
            int date = xml.indexOf("@+id/agenda_event_date\"");
            assertTrue(path, day > 0 && date > 0);
            assertTrue(path + ": weekday must come before the number", day < date);
        }
    }

    @Test
    public void recycledRowsOfTheOtherStyleAreNotReused() throws Exception {
        // The list recycles rows across style changes. A classic row handed to the card adapter
        // (or the reverse) kept the old look; the guard drops it and inflates a row of the right
        // style. The tag records the style each row was bound for.
        String source = read(AGENDA_ADAPTER);
        String getView = method(source, "public View getView(int position, View convertView, ViewGroup parent)");
        assertTrue(getView.contains("canReuseRow(convertView.getTag(), mCardStyle)"));
        assertTrue(getView.contains("convertView = null"));
        assertTrue(method(source, "static boolean canReuseRow(")
                .contains("((ViewHolder) tag).cardLayout == cardStyle"));
        assertTrue(source.contains("holder.cardLayout = mCardStyle;"));
    }

    @Test
    public void standaloneAgendaUsesCardRowsAndMonthPanelDoesNot() throws Exception {
        // Entry-to-layout transfer: the standalone agenda passes cardStyle=true, the month
        // secondary panel and the search keep the classic rows. No static flag decides it.
        String allInOne = read(ALL_IN_ONE);
        assertTrue(allInOne.contains("new AgendaFragment(timeMillis, false, true)"));
        assertTrue(allInOne.contains("new AgendaFragment(timeMillis, false)"));
        String search = read(SEARCH);
        assertTrue(search.contains("new AgendaFragment(timeMillis, true)"));
        String fragment = read(AGENDA_FRAGMENT);
        assertFalse("no static style flag", fragment.matches("(?s).*static\\s+boolean\\s+m?[cC]ardStyle.*"));
    }

    @Test
    public void restoredCardStyleReachesTheRowAdapterOnRotation() throws Exception {
        // Rotation recreates the fragment with the no-argument constructor (classic style). The
        // saved style is read in onCreate and applied to the list in onCreateView.
        String fragment = read(AGENDA_FRAGMENT);
        assertTrue(fragment.contains("outState.putBoolean(BUNDLE_KEY_CARD_STYLE, mCardStyle)"));
        assertTrue(fragment.contains("mCardStyle = icicle.getBoolean(BUNDLE_KEY_CARD_STYLE, mCardStyle)"));
        assertTrue(fragment.contains("mAgendaListView.setCardStyle(mCardStyle)"));
    }

    @Test
    public void rowAdapterChoosesItsLayoutByStyle() throws Exception {
        String byDay = read(BY_DAY);
        assertTrue(byDay.contains("cardStyle ? R.layout.agenda_item_card : R.layout.agenda_item"));
    }
}
