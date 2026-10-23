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

/**
 * Visual rules for one agenda card, decided only from the event's flags. This class has no
 * Android types so the rules can be checked by JVM tests. AgendaAdapter calls it on every bind,
 * so a recycled view never keeps the strike-through, outline or fill of an earlier item.
 */
public final class AgendaItemStyle {

    /** How the card is filled. */
    public enum Fill {
        /** A full block in the event colour. */
        SOLID,
        /** A declined event: the event colour, faded. */
        FADED,
        /** An invitation not yet answered: a surface card with an event-colour outline. */
        OUTLINED
    }

    public final Fill fill;
    public final boolean strikeThrough;
    public final boolean compact;

    private AgendaItemStyle(Fill fill, boolean strikeThrough, boolean compact) {
        this.fill = fill;
        this.strikeThrough = strikeThrough;
        this.compact = compact;
    }

    /**
     * @param declined the user declined the event
     * @param invited the user was invited and has not answered
     * @param canceled the event was canceled (title is struck through)
     * @param allDay all-day events are compact; timed events grow with their content
     */
    public static AgendaItemStyle resolve(boolean declined, boolean invited, boolean canceled,
            boolean allDay) {
        final Fill fill;
        if (declined) {
            fill = Fill.FADED;
        } else if (invited) {
            fill = Fill.OUTLINED;
        } else {
            fill = Fill.SOLID;
        }
        return new AgendaItemStyle(fill, canceled, allDay);
    }
}
