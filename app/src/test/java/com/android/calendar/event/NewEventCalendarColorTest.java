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

package com.android.calendar.event;

import static org.junit.Assert.assertEquals;

import com.android.calendar.event.CalendarAccountGroups.Calendar;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** The block's colour is the colour of the calendar the editor would pick for a new event. */
public class NewEventCalendarColorTest {

    private static Calendar calendar(long id, String name, String owner, String account,
            String type, int color) {
        return new Calendar(id, name, owner, account, type, color);
    }

    @Test
    public void storedDefaultCalendarColourIsUsed() {
        List<Calendar> calendars = new ArrayList<>();
        calendars.add(calendar(1, "Personal", "a@example.com", "a@example.com",
                "com.google", 0xFFFF0000));
        calendars.add(calendar(2, "Work", "b@example.com", "b@example.com",
                "com.google", 0xFF00FF00));
        assertEquals(0xFF00FF00, NewEventCalendarColor.colorFor(calendars, "b@example.com/Work"));
    }

    @Test
    public void colourIsNeverTheColourOfAnotherCalendar() {
        List<Calendar> calendars = new ArrayList<>();
        calendars.add(calendar(1, "Personal", "a@example.com", "a@example.com",
                "com.google", 0xFFFF0000));
        calendars.add(calendar(2, "Work", "b@example.com", "b@example.com",
                "com.google", 0xFF00FF00));
        int chosen = NewEventCalendarColor.colorFor(calendars, "a@example.com/Personal");
        assertEquals(0xFFFF0000, chosen);
    }

    @Test
    public void withoutAStoredDefaultTheOnlyCalendarIsUsed() {
        List<Calendar> calendars = new ArrayList<>();
        calendars.add(calendar(7, "Holidays", "h@example.com", "h@example.com",
                "com.google", 0xFF123456));
        assertEquals(0xFF123456, NewEventCalendarColor.colorFor(calendars, null));
    }

    @Test
    public void noWritableCalendarGivesZeroSoTheThemeIsUsed() {
        assertEquals(0, NewEventCalendarColor.colorFor(new ArrayList<Calendar>(), "x/y"));
    }
}
