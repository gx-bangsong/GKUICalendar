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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Where the current-time line and its dot go: today's column only, inside the grid. */
public class NowIndicatorTest {

    private static final int FIRST = 100;

    @Test
    public void lineAppearsInTodayColumnOnlyForEveryVisibleRange() {
        for (int numDays : new int[] {1, 3, 7}) {
            for (int today = FIRST - 2; today < FIRST + numDays + 2; today++) {
                int hits = 0;
                int hitColumn = -1;
                for (int column = 0; column < numDays; column++) {
                    if (DayGridGeometry.todayColumn(today, FIRST, numDays) == column) {
                        hits++;
                        hitColumn = column;
                    }
                }
                boolean todayVisible = today >= FIRST && today < FIRST + numDays;
                assertEquals("numDays=" + numDays + " today=" + today,
                        todayVisible ? 1 : 0, hits);
                if (todayVisible) {
                    assertEquals(today - FIRST, hitColumn);
                }
            }
        }
    }

    @Test
    public void nothingIsShownWhenTodayIsNotInTheRange() {
        assertEquals(-1, DayGridGeometry.todayColumn(FIRST - 1, FIRST, 7));
        assertEquals(-1, DayGridGeometry.todayColumn(FIRST + 7, FIRST, 7));
    }

    @Test
    public void lineYFollowsTheHourLinesAndStaysInsideTheDay() {
        float cell = 60f;
        float gap = 1f;
        float lastMinute = DayGridGeometry.minuteToY(1439f, cell, gap);
        assertTrue(lastMinute < 24 * (cell + gap));
        assertEquals(9 * (cell + gap),
                DayGridGeometry.minuteToY(9 * 60f, cell, gap), 0.001f);
    }

    @Test
    public void dotNeverCrossesItsColumnEdgeAtAnyInset() {
        float left = 40f;
        float radius = 4f;
        for (float inset = 0f; inset <= 3f; inset += 0.5f) {
            assertTrue(DayGridGeometry.nowDotCentreX(left, radius, inset) - radius >= left);
        }
    }
}
