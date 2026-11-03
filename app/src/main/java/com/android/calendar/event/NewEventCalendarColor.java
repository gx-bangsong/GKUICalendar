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

import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract.Calendars;

import com.android.calendar.Utils;
import com.android.calendar.settings.GeneralPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The colour of the calendar a new event opens with. It uses the same choice as the event editor
 * for a new event: the stored default calendar, or the editor's own fallback. The editor's
 * default-calendar rule lives in {@link CalendarAccountGroups#defaultCalendar}, so both use one
 * rule.
 */
public final class NewEventCalendarColor {

    private NewEventCalendarColor() {
    }

    /**
     * Display colour of the default calendar for a new event, or 0 when no writable calendar can
     * be read. This runs when a time block starts, on the touch path. It never runs from onDraw.
     */
    public static int resolve(Context context) {
        List<CalendarAccountGroups.Calendar> calendars = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(Calendars.CONTENT_URI,
                EditEventHelper.CALENDARS_PROJECTION,
                EditEventHelper.CALENDARS_WHERE_WRITEABLE_VISIBLE, null, null)) {
            if (cursor == null) {
                return 0;
            }
            while (cursor.moveToNext()) {
                calendars.add(new CalendarAccountGroups.Calendar(
                        cursor.getLong(EditEventHelper.CALENDARS_INDEX_ID),
                        cursor.getString(EditEventHelper.CALENDARS_INDEX_DISPLAY_NAME),
                        cursor.getString(EditEventHelper.CALENDARS_INDEX_OWNER_ACCOUNT),
                        cursor.getString(EditEventHelper.CALENDARS_INDEX_ACCOUNT_NAME),
                        cursor.getString(EditEventHelper.CALENDARS_INDEX_ACCOUNT_TYPE),
                        Utils.getDisplayColorFromColor(context,
                                cursor.getInt(EditEventHelper.CALENDARS_INDEX_COLOR))));
            }
        } catch (SecurityException e) {
            // No calendar permission: no colour can be determined, so the caller uses its theme.
            return 0;
        }
        String defaultKey = Utils.getSharedPreference(context,
                GeneralPreferences.KEY_DEFAULT_CALENDAR, (String) null);
        return colorFor(calendars, defaultKey);
    }

    /**
     * The colour of the calendar the editor would pick for a new event, from the already-read
     * list and the stored default key. Returns 0 when the list is empty. Pure, so it is tested.
     */
    public static int colorFor(List<CalendarAccountGroups.Calendar> calendars, String defaultKey) {
        CalendarAccountGroups.Calendar chosen = CalendarAccountGroups.defaultCalendar(
                CalendarAccountGroups.group(calendars), defaultKey);
        return chosen == null ? 0 : chosen.color;
    }
}
