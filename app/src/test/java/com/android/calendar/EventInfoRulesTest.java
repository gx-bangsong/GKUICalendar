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

import android.provider.CalendarContract.Calendars;

import org.junit.Test;

/**
 * Pure rules of the event detail page. They cover who may change an event, when the quick
 * reschedule card is offered, when the colour mark is drawn, and when a text row has content.
 * Real queries, saves and the rendered page need an on-device run.
 */
public class EventInfoRulesTest {

    @Test
    public void readOnlyAndFreeBusyCalendarsCannotBeChanged() {
        assertFalse(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_NONE));
        assertFalse(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_FREEBUSY));
        assertFalse(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_READ));
        assertFalse(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_RESPOND));
        assertFalse(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_OVERRIDE));
    }

    @Test
    public void contributorAndAboveCanChangeTheCalendar() {
        assertTrue(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_CONTRIBUTOR));
        assertTrue(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_EDITOR));
        assertTrue(EventInfoRules.canModifyCalendar(Calendars.CAL_ACCESS_OWNER));
    }

    @Test
    public void eventNeedsWritableCalendarAndOrganizer() {
        // Read-only calendar: never editable, even for the organizer.
        assertFalse(EventInfoRules.canModifyEvent(false, true));
        // Writable calendar, but the user is only an attendee: not editable.
        assertFalse(EventInfoRules.canModifyEvent(true, false));
        assertTrue(EventInfoRules.canModifyEvent(true, true));
    }

    @Test
    public void rescheduleIsOfferedOnlyForASavedEditableEvent() {
        assertTrue(EventInfoRules.showReschedule(true, 42L));
        assertFalse(EventInfoRules.showReschedule(false, 42L));
        assertFalse(EventInfoRules.showReschedule(true, 0L));
        assertFalse(EventInfoRules.showReschedule(true, -1L));
    }

    @Test
    public void colourMarkIsDrawnOnlyForAKnownColour() {
        // -1 is the page's "no colour resolved yet" value: no mark, not a black or white bar.
        assertFalse(EventInfoRules.hasColorMarker(-1));
        assertTrue(EventInfoRules.hasColorMarker(0xFF3F51B5));
        assertTrue(EventInfoRules.hasColorMarker(0xFF000000));
    }

    @Test
    public void blankTextDoesNotCreateARow() {
        assertFalse(EventInfoRules.hasText(null));
        assertFalse(EventInfoRules.hasText(""));
        assertFalse(EventInfoRules.hasText("   \n\t "));
        assertTrue(EventInfoRules.hasText("  Room 4  "));
        assertTrue(EventInfoRules.hasText("x"));
    }
}
