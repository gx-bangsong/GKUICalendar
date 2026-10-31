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

    /**
     * The selection outline shows only on the item that is selected while the list is showing
     * the event (the two-pane tablet case). AgendaWindowAdapter sets it on every bind.
     */
    public static boolean selectionOutlineVisible(boolean selected, boolean showEventOnStart) {
        return selected && showEventOnStart;
    }

    /** How a selected row is marked on the tablet two-pane layout. */
    public enum Selection {
        /** No selection feedback (phone, or details are not shown beside the list). */
        NONE,
        /**
         * Card rows (standalone agenda). Only the outline is the selection. The row background
         * and the text colours belong to the card and are not repainted.
         */
        CARD_OUTLINE,
        /** Classic month panel rows. The whole row is repainted and the text recoloured. */
        CLASSIC_ROW
    }

    /**
     * Picks the selection feedback for a row. A card row never gets {@link Selection#CLASSIC_ROW}:
     * that path reads the classic GridLayout params, which card rows do not have.
     */
    public static Selection selectionFeedback(boolean cardStyle, boolean showEventOnStart) {
        if (!showEventOnStart) {
            return Selection.NONE;
        }
        return cardStyle ? Selection.CARD_OUTLINE : Selection.CLASSIC_ROW;
    }

    /**
     * The date column shows on the first event row of each day. Later rows of the same day keep
     * the column in place but hidden, so the event blocks stay aligned.
     *
     * @param firstRow this is the first row of the list
     * @param previousIsEvent the row before this one is an event row
     * @param previousDay the Julian day of the row before this one
     * @param day the Julian day of this row
     */
    public static boolean isFirstEventOfDay(boolean firstRow, boolean previousIsEvent,
            int previousDay, int day) {
        return firstRow || !previousIsEvent || previousDay != day;
    }
}
