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

import org.junit.Test;

/**
 * JVM tests for the agenda card rules: fill by attendee state, strike-through, and compact
 * all-day items. They also check that a recycled view gets its state back from the inputs.
 */
public class AgendaItemStyleTest {

    @Test
    public void normalEventIsSolidAndNotStruck() {
        AgendaItemStyle s = AgendaItemStyle.resolve(false, false, false, false);
        assertEquals(AgendaItemStyle.Fill.SOLID, s.fill);
        assertFalse(s.strikeThrough);
        assertFalse(s.compact);
    }

    @Test
    public void declinedIsFadedAndInvitedIsOutlined() {
        assertEquals(AgendaItemStyle.Fill.FADED,
                AgendaItemStyle.resolve(true, false, false, false).fill);
        assertEquals(AgendaItemStyle.Fill.OUTLINED,
                AgendaItemStyle.resolve(false, true, false, false).fill);
    }

    @Test
    public void declinedWinsOverInvited() {
        assertEquals(AgendaItemStyle.Fill.FADED,
                AgendaItemStyle.resolve(true, true, false, false).fill);
    }

    @Test
    public void canceledEventIsStruckThrough() {
        assertTrue(AgendaItemStyle.resolve(false, false, true, false).strikeThrough);
    }

    @Test
    public void recycledViewGetsStrikeBackOff() {
        // The same view is bound first to a canceled event, then to a normal one.
        AgendaItemStyle first = AgendaItemStyle.resolve(false, false, true, false);
        AgendaItemStyle second = AgendaItemStyle.resolve(false, false, false, false);
        assertTrue(first.strikeThrough);
        assertFalse(second.strikeThrough);
    }

    @Test
    public void recycledViewGetsOutlineBackOff() {
        AgendaItemStyle invited = AgendaItemStyle.resolve(false, true, false, false);
        AgendaItemStyle normal = AgendaItemStyle.resolve(false, false, false, false);
        assertEquals(AgendaItemStyle.Fill.OUTLINED, invited.fill);
        assertEquals(AgendaItemStyle.Fill.SOLID, normal.fill);
    }

    @Test
    public void allDayIsCompactAndTimedIsNot() {
        assertTrue(AgendaItemStyle.resolve(false, false, false, true).compact);
        assertFalse(AgendaItemStyle.resolve(false, false, false, false).compact);
    }

    @Test
    public void selectionOutlineShowsOnlyForTheSelectedItemWhileTheListShowsEvents() {
        assertTrue(AgendaItemStyle.selectionOutlineVisible(true, true));
        assertFalse(AgendaItemStyle.selectionOutlineVisible(false, true));
        // Without the two-pane event display no item shows the outline, selected or not.
        assertFalse(AgendaItemStyle.selectionOutlineVisible(true, false));
    }

    @Test
    public void cardRowsNeverTakeTheClassicGridSelectionPath() {
        // The crash came from the classic GridLayout params on a card row. A card row must
        // always get the outline-only path on the tablet two-pane layout.
        assertEquals(AgendaItemStyle.Selection.CARD_OUTLINE,
                AgendaItemStyle.selectionFeedback(true, true));
    }

    @Test
    public void classicMonthPanelKeepsItsRowRepaintPath() {
        assertEquals(AgendaItemStyle.Selection.CLASSIC_ROW,
                AgendaItemStyle.selectionFeedback(false, true));
    }

    @Test
    public void noSelectionFeedbackWithoutTheTwoPaneEventDisplay() {
        assertEquals(AgendaItemStyle.Selection.NONE,
                AgendaItemStyle.selectionFeedback(true, false));
        assertEquals(AgendaItemStyle.Selection.NONE,
                AgendaItemStyle.selectionFeedback(false, false));
    }

    @Test
    public void dateShowsOnTheFirstEventRowOfEachDay() {
        // First row of the list.
        assertTrue(AgendaItemStyle.isFirstEventOfDay(true, false, 0, 100));
        // Previous row is a day header, not an event.
        assertTrue(AgendaItemStyle.isFirstEventOfDay(false, false, 100, 100));
        // Previous event is on an earlier day.
        assertTrue(AgendaItemStyle.isFirstEventOfDay(false, true, 99, 100));
    }

    @Test
    public void laterEventsOfTheSameDayDoNotRepeatTheDate() {
        assertFalse(AgendaItemStyle.isFirstEventOfDay(false, true, 100, 100));
    }
}
