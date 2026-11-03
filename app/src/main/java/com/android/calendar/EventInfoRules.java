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

import android.provider.CalendarContract.Calendars;

/**
 * Pure rules of the event detail page: who may change the event, when the quick reschedule
 * card is offered, which colour marks the event, and when a text row has content. They have no
 * view or cursor dependency, so JVM tests can cover them. EventInfoFragment asks these questions
 * and keeps all queries, saves and updates itself.
 */
public final class EventInfoRules {

    private EventInfoRules() {
    }

    /** The calendar lets the user change its events (contributor or above). */
    public static boolean canModifyCalendar(int calendarAccessLevel) {
        return calendarAccessLevel >= Calendars.CAL_ACCESS_CONTRIBUTOR;
    }

    /**
     * The event itself may be changed only when its calendar allows changes and the user is the
     * organizer. A read-only or free/busy calendar never gains this, whatever the organizer says.
     */
    public static boolean canModifyEvent(boolean canModifyCalendar, boolean isOrganizer) {
        return canModifyCalendar && isOrganizer;
    }

    /**
     * The quick reschedule card is offered only for a saved event the user may change. The
     * reschedule itself keeps the same event ID and instance rules as before.
     */
    public static boolean showReschedule(boolean canModifyEvent, long eventId) {
        return canModifyEvent && eventId > 0;
    }

    /**
     * The single colour mark of the page. The colour is the already resolved event colour, which
     * falls back to the calendar colour. Returns -1 when no colour is known, and then no mark is
     * drawn.
     */
    public static boolean hasColorMarker(int resolvedColor) {
        return resolvedColor != -1;
    }

    /** A text row has content only when there is something other than whitespace. */
    public static boolean hasText(String text) {
        return text != null && text.trim().length() != 0;
    }
}
