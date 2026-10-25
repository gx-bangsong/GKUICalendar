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

    /**
     * Visible gap between two neighbouring hour blocks. The slot already contains the one-pixel
     * slot gap, and each block is inset from both edges of its slot, so the gap is two insets.
     */
    static float hourBlockGap(float inset) {
        return 2f * inset;
    }

    /** Horizontal centre of a day column, used for the header and the date circle. */
    static float columnCenter(float left, float right) {
        return (left + right) / 2f;
    }

    /**
     * Y of a time of day, in grid coordinates. {@code minute} counts from midnight. Hour lines sit
     * at {@code hour * (cellHeight + hourGap)}, so minute 60 * h lands on the h-th hour line. The
     * current-time line, the time block edges and their labels all use this function.
     */
    static float minuteToY(float minute, float cellHeight, float hourGap) {
        return minute * (cellHeight + hourGap) / 60f;
    }

    /** Index of today among the visible columns, or -1 when today is not visible. */
    static int todayColumn(int todayJulianDay, int firstJulianDay, int numDays) {
        int index = todayJulianDay - firstJulianDay;
        return index >= 0 && index < numDays ? index : -1;
    }

    /**
     * Whether the view draws rounded hour blocks. Only the single day view does, and only when the
     * background mask is on. Otherwise the hours are separated by the thin grid lines.
     */
    static boolean drawsRoundedHourBlocks(int numDays, boolean backgroundMaskOn) {
        return numDays == 1 && backgroundMaskOn;
    }

    /**
     * Radius of the hole cut from the block fill for a hollow handle. It is the inside of the
     * handle ring, so the fill stops at the inner edge of the stroke.
     */
    static float handleFillHoleRadius(float radius, float strokeWidth) {
        return Math.max(0f, radius - strokeWidth / 2f);
    }

    /**
     * Radius of the gap cut from the block outline around a hollow handle. It reaches the outer
     * edge of the handle ring, so the outline meets the ring and does not cross the hole.
     */
    static float handleOutlineHoleRadius(float radius, float strokeWidth) {
        return radius + strokeWidth / 2f;
    }

    /**
     * Centre X of the current-time dot. The dot starts {@code inset} to the right of the column
     * edge, so its whole circle stays inside the column and clear of the hour labels.
     */
    static float nowDotCentreX(float columnLeft, float dotRadius, float inset) {
        return columnLeft + inset + dotRadius;
    }

    /**
     * Left edge of a day column. Day 0 starts after the time-label column. DayView uses this for
     * computeDayLeftPosition, so tests exercise the same arithmetic as the drawing code.
     */
    static int dayColumnLeft(int day, int numDays, int viewWidth, int hoursWidth) {
        return day * (viewWidth - hoursWidth) / numDays + hoursWidth;
    }

    /**
     * Horizontal centre of one day's header block (weekday, date number and circle).
     * Single day: the centre of the left time-label band, from {@code hoursLeftMargin} to
     * {@code hoursWidth - hoursRightMargin}. Hour labels are drawn from the left margin, so the
     * band is the width of the widest label. The centre depends only on the label column, so it
     * does not move when the view is widened. Several days: the centre of that day's own column.
     */
    static float dateHeaderCentreX(int numDays, float dayLeft, float dayRight, float hoursWidth,
            float hoursLeftMargin, float hoursRightMargin) {
        if (numDays == 1) {
            return (hoursLeftMargin + hoursWidth - hoursRightMargin) / 2f;
        }
        return columnCenter(dayLeft, dayRight);
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

    /**
     * The corner radius of an event block. It never exceeds half the block height, so a short
     * event (for example a 15-minute one) is a rounded bar, not a shape whose corners overlap.
     */
    static float eventCornerRadius(float blockHeight, float maxRadius) {
        return Math.max(0f, Math.min(maxRadius, blockHeight / 2f));
    }
}
