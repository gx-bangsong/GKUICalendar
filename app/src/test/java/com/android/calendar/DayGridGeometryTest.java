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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.calendar.DayGridGeometry.DateState;

import java.time.LocalDate;

import org.junit.Test;

/**
 * JVM tests for the shared day and week grid rules. They check logic only, not drawing.
 */
public class DayGridGeometryTest {

    private static final float DENSITY = 2.625f;
    private static final float ONE_PX = 1f;
    private static final float INSET = 1.5f * DENSITY;

    // ---- Date state: today and selected are separate inputs, one visible state ----

    @Test
    public void todayAndSelectedTogetherIsOneTodayState() {
        assertEquals(DateState.TODAY, DayGridGeometry.dateState(2461000, 2461000, 2461000));
    }

    @Test
    public void selectedNonTodayIsSelectedState() {
        assertEquals(DateState.SELECTED, DayGridGeometry.dateState(2461001, 2461000, 2461001));
    }

    @Test
    public void todayOnlyIsTodayAndOtherDaysAreNormal() {
        assertEquals(DateState.TODAY, DayGridGeometry.dateState(2461000, 2461000, 2461005));
        assertEquals(DateState.NORMAL, DayGridGeometry.dateState(2461003, 2461000, 2461005));
    }

    @Test
    public void crossMonthBoundaryUsesConsecutiveDays() {
        // 31 Oct 2026 is today; 1 Nov 2026 is selected. Julian days are consecutive across months.
        int oct31 = (int) LocalDate.of(2026, 10, 31).toEpochDay();
        int nov1 = (int) LocalDate.of(2026, 11, 1).toEpochDay();
        assertEquals(1, nov1 - oct31);
        assertEquals(DateState.TODAY, DayGridGeometry.dateState(oct31, oct31, nov1));
        assertEquals(DateState.SELECTED, DayGridGeometry.dateState(nov1, oct31, nov1));
    }

    @Test
    public void noSelectionMeansNoSelectedState() {
        assertEquals(DateState.NORMAL, DayGridGeometry.dateState(2461003, 2461000, -1));
    }

    // ---- Hour blocks: rounded inside each slot, time maths unchanged ----

    @Test
    public void nineOClockBlockStartsInsideNineOClockSlot() {
        float slot = 40f * DENSITY + ONE_PX;
        float top = DayGridGeometry.hourBlockTop(9, slot, INSET);
        float bottom = DayGridGeometry.hourBlockBottom(9, slot, INSET);
        assertEquals(9 * slot, top - INSET, 0.0001f);
        assertTrue(bottom <= 10 * slot);
        assertTrue(bottom > top);
    }

    @Test
    public void adjacentHourBlocksHaveStableGapBetweenTwoAndFourDp() {
        float slot = 40f * DENSITY + ONE_PX;
        for (int hour = 0; hour < 23; hour++) {
            float gap = DayGridGeometry.hourBlockTop(hour + 1, slot, INSET)
                    - DayGridGeometry.hourBlockBottom(hour, slot, INSET);
            assertEquals(DayGridGeometry.hourBlockGap(INSET), gap, 0.0001f);
            float gapDp = gap / DENSITY;
            assertTrue("gap dp " + gapDp, gapDp >= 2f && gapDp <= 4f);
        }
    }

    @Test
    public void hourBlockNeverLeavesItsSlot() {
        float slot = 17f * DENSITY + ONE_PX;
        for (int hour = 0; hour < 24; hour++) {
            assertTrue(DayGridGeometry.hourBlockTop(hour, slot, INSET) >= hour * slot);
            assertTrue(DayGridGeometry.hourBlockBottom(hour, slot, INSET) <= (hour + 1) * slot);
        }
    }

    @Test
    public void columnCentreIsMidpointOfColumn() {
        assertEquals(150f, DayGridGeometry.columnCenter(100f, 200f), 0.0001f);
    }

    // ---- Rounded ends of clipped events ----

    @Test
    public void roundedTopOnlyShownWhenStartInsideViewport() {
        assertFalse(DayGridGeometry.hasVisibleRoundedTop(-5f, 0f));
        assertTrue(DayGridGeometry.hasVisibleRoundedTop(0f, 0f));
        assertTrue(DayGridGeometry.hasVisibleRoundedTop(30f, 0f));
    }

    @Test
    public void roundedBottomOnlyShownWhenEndInsideViewport() {
        assertFalse(DayGridGeometry.hasVisibleRoundedBottom(120f, 100f));
        assertTrue(DayGridGeometry.hasVisibleRoundedBottom(100f, 100f));
    }

    // ---- Date header geometry and FontMetrics centring ----

    @Test
    public void headerHeightIncludesEveryOptionalLine() {
        float base = DayGridGeometry.dateHeaderHeight(4f, 14f, 2f, 32f, 0f, 0, 0f, 4f);
        assertEquals(56f, base, 0.0001f);

        float withLunarAndTwoSubs =
                DayGridGeometry.dateHeaderHeight(4f, 14f, 2f, 32f, 16f, 2, 12f, 4f);
        assertEquals(base + 16f + 24f, withLunarAndTwoSubs, 0.0001f);
    }

    @Test
    public void negativeSubscriptionCountIsIgnored() {
        float base = DayGridGeometry.dateHeaderHeight(4f, 14f, 2f, 32f, 0f, 0, 0f, 4f);
        assertEquals(base, DayGridGeometry.dateHeaderHeight(4f, 14f, 2f, 32f, 0f, -3, 12f, 4f),
                0.0001f);
    }

    @Test
    public void centredBaselineCentresGlyphBoxOnCircleCentre() {
        // Ascent is negative. The glyph box spans baseline+ascent to baseline+descent.
        float ascent = -10f;
        float descent = 2f;
        float centerY = 20f;
        float baseline = DayGridGeometry.centredBaseline(centerY, ascent, descent);
        float boxTop = baseline + ascent;
        float boxBottom = baseline + descent;
        assertEquals(centerY, (boxTop + boxBottom) / 2f, 0.0001f);
    }

    @Test
    public void shortEventRadiusIsCappedAtHalfItsHeight() {
        // A 10 px bar can have at most a 5 px radius, so its corners never overlap.
        assertEquals(5f, DayGridGeometry.eventCornerRadius(10f, 12f), 0.001f);
    }

    @Test
    public void tallEventKeepsTheFullRadius() {
        assertEquals(12f, DayGridGeometry.eventCornerRadius(60f, 12f), 0.001f);
    }

    @Test
    public void degenerateHeightGivesNoRadius() {
        assertEquals(0f, DayGridGeometry.eventCornerRadius(0f, 12f), 0.001f);
        assertEquals(0f, DayGridGeometry.eventCornerRadius(-4f, 12f), 0.001f);
    }

    // ---------- Date header position (single day in the time column, week per column) ----------

    /** Time-label column width and the label margins, as in the phone dimens (4dp each side). */
    private static final int HOURS_WIDTH = 44;
    private static final int HOURS_LEFT = 4;
    private static final int HOURS_RIGHT = 4;
    private static final int[] WIDTHS = {360, 412, 600, 720, 1024, 1280};

    private static float singleDayCentre(int viewWidth) {
        int left = DayGridGeometry.dayColumnLeft(0, 1, viewWidth, HOURS_WIDTH);
        int right = DayGridGeometry.dayColumnLeft(1, 1, viewWidth, HOURS_WIDTH);
        return DayGridGeometry.dateHeaderCentreX(1, left, right, HOURS_WIDTH,
                HOURS_LEFT, HOURS_RIGHT);
    }

    @Test
    public void singleDayColumnStartsAfterTheTimeLabelsAtEveryWidth() {
        for (int width : WIDTHS) {
            assertEquals(HOURS_WIDTH, DayGridGeometry.dayColumnLeft(0, 1, width, HOURS_WIDTH));
        }
    }

    @Test
    public void singleDayDateCentreSitsInTheTimeLabelBand() {
        for (int width : WIDTHS) {
            float cx = singleDayCentre(width);
            assertTrue("centre " + cx + " left of the label band at width " + width,
                    cx >= HOURS_LEFT);
            assertTrue("centre " + cx + " right of the label band at width " + width,
                    cx <= HOURS_WIDTH - HOURS_RIGHT);
            // Strictly inside the time column, so the all-day and event area on the right is
            // never under the date block.
            assertTrue(cx < HOURS_WIDTH);
        }
        assertEquals((HOURS_LEFT + HOURS_WIDTH - HOURS_RIGHT) / 2f, singleDayCentre(720), 0.001f);
    }

    @Test
    public void singleDayDateCentreDoesNotMoveIntoTheEventAreaWhenWidened() {
        float narrow = singleDayCentre(360);
        float wide = singleDayCentre(1280);
        assertEquals(narrow, wide, 0.001f);
        // The old centring put the date over the event column, which moves right as the view
        // grows. The label-column centre must not follow it.
        float eventColumnCentre = DayGridGeometry.columnCenter(HOURS_WIDTH, 1280);
        assertTrue(Math.abs(wide - eventColumnCentre) > HOURS_WIDTH);
    }

    @Test
    public void multiDayDateCentreMatchesEachColumn() {
        for (int numDays : new int[] {3, 7}) {
            int width = 720;
            for (int day = 0; day < numDays; day++) {
                int left = DayGridGeometry.dayColumnLeft(day, numDays, width, HOURS_WIDTH);
                int right = DayGridGeometry.dayColumnLeft(day + 1, numDays, width, HOURS_WIDTH);
                float cx = DayGridGeometry.dateHeaderCentreX(numDays, left, right, HOURS_WIDTH,
                        HOURS_LEFT, HOURS_RIGHT);
                assertEquals(DayGridGeometry.columnCenter(left, right), cx, 0.001f);
                assertTrue(cx > left && cx < right);
            }
            // The columns run from the label edge to the right edge of the view.
            assertEquals(width, DayGridGeometry.dayColumnLeft(numDays, numDays, width,
                    HOURS_WIDTH));
        }
    }

    // Current-time line, background mask and handle geometry (fix pass 2).

    @Test
    public void minuteToYLandsOnHourLinesAtEveryZoom() {
        // Zoom changes the cell height. The time mapping must still hit the hour lines exactly.
        for (float cell : new float[] {30f, 60f, 120f}) {
            for (int hour = 0; hour <= 24; hour++) {
                assertEquals(hour * (cell + 1f),
                        DayGridGeometry.minuteToY(60f * hour, cell, 1f), 0.001f);
            }
        }
    }

    @Test
    public void minuteToYIsLinearInsideAnHour() {
        assertEquals(0f, DayGridGeometry.minuteToY(0f, 60f, 1f), 0f);
        assertEquals(579.5f, DayGridGeometry.minuteToY(570f, 60f, 1f), 0.001f);
    }

    @Test
    public void todayColumnIsOnlyTodayWhenVisible() {
        assertEquals(0, DayGridGeometry.todayColumn(98, 98, 1));
        assertEquals(-1, DayGridGeometry.todayColumn(100, 98, 1));
        assertEquals(2, DayGridGeometry.todayColumn(100, 98, 3));
        assertEquals(-1, DayGridGeometry.todayColumn(97, 98, 7));
        assertEquals(-1, DayGridGeometry.todayColumn(105, 98, 7));
    }

    @Test
    public void roundedHourBlocksAreDrawnForDayAndWeekAlike() {
        // The function has no mask parameter: the base layer is the same with the mask off or on.
        assertTrue(DayGridGeometry.drawsRoundedHourBlocks(1));
        assertTrue(DayGridGeometry.drawsRoundedHourBlocks(3));
        assertTrue(DayGridGeometry.drawsRoundedHourBlocks(7));
        assertFalse(DayGridGeometry.drawsRoundedHourBlocks(0));
    }

    @Test
    public void wideBlockKeepsItsFullCorner() {
        // 40dp wide, 60dp tall: a quarter of the shorter side is 10dp, so the 8dp corner stays.
        float radius = DayGridGeometry.hourBlockRadius(8f * DENSITY, 40f * DENSITY, 60f * DENSITY);
        assertEquals(8f * DENSITY, radius, 0.0001f);
    }

    @Test
    public void narrowWeekColumnRadiusIsLimitedNotACapsule() {
        // 20dp wide: a quarter of 20dp is 5dp, so the corner shrinks instead of making a capsule.
        float width = 20f * DENSITY;
        float radius = DayGridGeometry.hourBlockRadius(8f * DENSITY, width, 60f * DENSITY);
        assertEquals(width / 4f, radius, 0.0001f);
        assertTrue(radius < width / 2f);
    }

    @Test
    public void everyBlockRadiusStaysWithinAQuarterOfItsSmallerSide() {
        for (float width = 8f; width <= 80f; width += 1f) {
            for (float height = 10f; height <= 120f; height += 5f) {
                float r = DayGridGeometry.hourBlockRadius(8f * DENSITY, width * DENSITY,
                        height * DENSITY);
                assertTrue(r >= 0f);
                assertTrue(r <= Math.min(width, height) * DENSITY * 0.25f + 0.0001f);
            }
        }
    }

    // ---- Preview handles: START top-left inside, END bottom-right inside ----

    private static final float CORNER = 8f;
    private static final float RADIUS = 7f;

    @Test
    public void startHandleSitsNearTheLeftOfTheTopEdge() {
        float left = 0f;
        float right = 300f;
        float x = DayGridGeometry.handleCentreX(true, left, right, CORNER, RADIUS);
        assertEquals(CORNER + RADIUS, x, 0.001f);
        assertTrue(x < (left + right) / 2f);
    }

    @Test
    public void endHandleSitsNearTheRightOfTheBottomEdge() {
        float left = 0f;
        float right = 300f;
        float x = DayGridGeometry.handleCentreX(false, left, right, CORNER, RADIUS);
        assertEquals(right - CORNER - RADIUS, x, 0.001f);
        assertTrue(x > (left + right) / 2f);
    }

    @Test
    public void handlesStayOnTheirSideWhenTheWindowWidens() {
        // Same inset from each side at any width: they do not drift back to the centre line.
        float[] widths = {200f, 400f, 900f};
        for (float w : widths) {
            float left = 10f;
            float right = left + w;
            float start = DayGridGeometry.handleCentreX(true, left, right, CORNER, RADIUS);
            float end = DayGridGeometry.handleCentreX(false, left, right, CORNER, RADIUS);
            assertEquals(CORNER + RADIUS, start - left, 0.001f);
            assertEquals(CORNER + RADIUS, right - end, 0.001f);
            assertTrue(start < end);
        }
    }

    @Test
    public void handleRingStaysInsideTheBlockWhenItFits() {
        for (float w = 2 * RADIUS; w <= 600f; w += 10f) {
            float left = 50f;
            float right = left + w;
            float start = DayGridGeometry.handleCentreX(true, left, right, CORNER, RADIUS);
            float end = DayGridGeometry.handleCentreX(false, left, right, CORNER, RADIUS);
            assertTrue(start - RADIUS >= left - 0.001f);
            assertTrue(end + RADIUS <= right + 0.001f);
        }
    }

    @Test
    public void narrowBlockPutsBothHandlesOnTheCentreLineInsteadOfOutside() {
        float left = 0f;
        float right = 12f;
        float start = DayGridGeometry.handleCentreX(true, left, right, CORNER, RADIUS);
        float end = DayGridGeometry.handleCentreX(false, left, right, CORNER, RADIUS);
        assertEquals(6f, start, 0.001f);
        assertEquals(6f, end, 0.001f);
    }

    @Test
    public void touchOnStartHandleSelectsStartOnly() {
        float sx = 15f;
        float sy = 100f;
        float ex = 285f;
        float ey = 300f;
        float touch = 24f;
        assertEquals(1, DayGridGeometry.nearestHandle(sx, sy, sx, sy, ex, ey, touch));
        assertEquals(2, DayGridGeometry.nearestHandle(ex, ey, sx, sy, ex, ey, touch));
    }

    @Test
    public void touchAwayFromBothHandlesDoesNothing() {
        // The middle of a tall block is not a handle, even though the old centre was there.
        assertEquals(0, DayGridGeometry.nearestHandle(150f, 200f, 15f, 100f, 285f, 300f, 24f));
    }

    @Test
    public void touchCentreIsNotAHandleAnyMore() {
        // Regression: the old shared centre X is 150. A touch there, at the start Y, must miss.
        assertEquals(0, DayGridGeometry.nearestHandle(150f, 100f, 15f, 100f, 285f, 300f, 24f));
    }

    @Test
    public void touchRangeIsWiderThanTheVisualRing() {
        // Visual ring is RADIUS; the touch test uses a larger radius, so a point just outside the
        // ring still hits the handle.
        float touch = 24f;
        assertEquals(1, DayGridGeometry.nearestHandle(15f + RADIUS + 5f, 100f, 15f, 100f, 285f,
                300f, touch));
    }

    @Test
    public void nearerHandleWinsWhenBothAreInReach() {
        // Handles are close together in a short block: the nearer one is taken.
        assertEquals(1, DayGridGeometry.nearestHandle(10f, 100f, 15f, 100f, 25f, 110f, 24f));
        assertEquals(2, DayGridGeometry.nearestHandle(24f, 110f, 15f, 100f, 25f, 110f, 24f));
    }

    @Test
    public void nowDotCentreKeepsTheWholeDotInsideTheColumn() {
        float left = 120f;
        float radius = 4f;
        float inset = 1f;
        float centre = DayGridGeometry.nowDotCentreX(left, radius, inset);
        assertEquals(125f, centre, 0f);
        assertTrue(centre - radius >= left);
    }

    @Test
    public void handleHolesSitInsideTheRingAndOutlineStopsAtItsOuterEdge() {
        float radius = 7f;
        float stroke = 2f;
        assertEquals(6f, DayGridGeometry.handleFillHoleRadius(radius, stroke), 0f);
        assertEquals(8f, DayGridGeometry.handleOutlineHoleRadius(radius, stroke), 0f);
        assertTrue(DayGridGeometry.handleFillHoleRadius(0.5f, 2f) >= 0f);
    }

    @Test
    public void futureStartYIsTheWholeColumnAfterToday() {
        // A column after today is future from its top, so the whole column is masked.
        assertEquals(Float.NEGATIVE_INFINITY, DayGridGeometry.futureStartY(2, 1, 300f), 0f);
    }

    @Test
    public void futureStartYIsTheNowLineInTodaysColumn() {
        assertEquals(300f, DayGridGeometry.futureStartY(1, 1, 300f), 0f);
    }

    @Test
    public void futureStartYIsNoneForColumnsBeforeToday() {
        assertEquals(Float.POSITIVE_INFINITY, DayGridGeometry.futureStartY(0, 1, 300f), 0f);
    }

    @Test
    public void futureStartYHandlesTodayOutsideTheVisibleRange() {
        // Today before the first visible column: every column is in the future.
        assertEquals(Float.NEGATIVE_INFINITY, DayGridGeometry.futureStartY(0, -1, 300f), 0f);
        // Today after the last visible column: nothing is masked.
        assertEquals(Float.POSITIVE_INFINITY, DayGridGeometry.futureStartY(6, 9, 300f), 0f);
    }
}
