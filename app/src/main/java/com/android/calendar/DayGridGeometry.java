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

/**
 * Pure rules shared by the day view and the classic week timeline: date header state, the
 * rounded hour blocks and the header height. No Android types are used here so that the rules
 * can be checked by JVM unit tests. DayView does the drawing and calls these helpers.
 *
 * <p>Visual insets never change the time maths: an hour slot still starts at
 * {@code hour * slotHeight}. The rounded block is only drawn inside that slot.
 */
final class DayGridGeometry {

    private DayGridGeometry() {
    }

    /** Date number states. Today wins over selected so that the two never stack. */
    enum DateState {
        NORMAL,
        SELECTED,
        TODAY
    }

    /**
     * Today and selected are computed separately and combined here. A day that is both today and
     * selected is {@link DateState#TODAY}.
     */
    static DateState dateState(int julianDay, int todayJulianDay, int selectedJulianDay) {
        final boolean isToday = julianDay == todayJulianDay;
        final boolean isSelected = julianDay == selectedJulianDay;
        if (isToday) {
            return DateState.TODAY;
        }
        return isSelected ? DateState.SELECTED : DateState.NORMAL;
    }

    /** Top of the rounded block for {@code hour}. The slot starts at {@code hour * slotHeight}. */
    static float hourBlockTop(int hour, float slotHeight, float inset) {
        return hour * slotHeight + inset;
    }

    /** Bottom of the rounded block for {@code hour}, kept inside its slot. */
    static float hourBlockBottom(int hour, float slotHeight, float inset) {
        return (hour + 1) * slotHeight - inset;
    }

    /** Visible gap between two neighbouring hour blocks: the slot gap plus both insets. */
    static float hourBlockGap(float slotGap, float inset) {
        return slotGap + 2f * inset;
    }

    /** Horizontal centre of a day column, used for the header and the date circle. */
    static float columnCenter(float left, float right) {
        return (left + right) / 2f;
    }

    /**
     * Baseline that centres text on {@code centerY} using FontMetrics. {@code ascent} is negative,
     * as in {@link android.graphics.Paint.FontMetrics}.
     */
    static float centredBaseline(float centerY, float ascent, float descent) {
        return centerY - (ascent + descent) / 2f;
    }

    /**
     * Header height, built from its parts from top to bottom: top padding, weekday line, gap,
     * date circle, optional lunar line, subscription lines, bottom padding.
     */
    static float dateHeaderHeight(float topPad, float weekdayLine, float gap, float circle,
            float lunarLine, int subscriptionLines, float subscriptionLine, float bottomPad) {
        float h = topPad + weekdayLine + gap + circle + lunarLine;
        h += Math.max(0, subscriptionLines) * subscriptionLine;
        return h + bottomPad;
    }

    /**
     * Top edge of a rounded event that may be clipped by the viewport. The rounded corner is
     * drawn only when the real top edge is inside the viewport. When the event starts above the
     * viewport, the top is cut flat at the viewport edge instead of showing a false round end.
     */
    static boolean hasVisibleRoundedTop(float top, float viewTop) {
        return top >= viewTop;
    }

    /** Same rule for the bottom edge. */
    static boolean hasVisibleRoundedBottom(float bottom, float viewBottom) {
        return bottom <= viewBottom;
    }
}
