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
}
