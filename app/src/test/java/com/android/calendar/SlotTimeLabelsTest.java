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

import org.junit.Test;

/** Layout rules of the block's start and end time chips, in the left time column. */
public class SlotTimeLabelsTest {

    @Test
    public void tallBlockKeepsEachLabelOnItsEdge() {
        float[] c = SlotTimeLabels.centres(100f, 300f, 20f);
        assertEquals(100f, c[0], 0.001f);
        assertEquals(300f, c[1], 0.001f);
    }

    @Test
    public void shortBlockSpreadsTheLabelsSymmetricallyAroundTheMiddle() {
        float[] c = SlotTimeLabels.centres(100f, 110f, 20f);
        assertEquals(95f, c[0], 0.001f);
        assertEquals(115f, c[1], 0.001f);
        assertFalse(SlotTimeLabels.overlaps(c[0], 20f, c[1], 20f));
    }

    @Test
    public void overlapCountsRealCollisionsButNotTouching() {
        assertTrue(SlotTimeLabels.overlaps(0f, 20f, 10f, 20f));
        assertFalse(SlotTimeLabels.overlaps(0f, 20f, 20f, 20f));
        assertFalse(SlotTimeLabels.overlaps(0f, 20f, 200f, 20f));
    }

    @Test
    public void labelsStayInsideTheVisibleGrid() {
        assertEquals(60f, SlotTimeLabels.clampCentre(0f, 20f, 50f, 500f), 0.001f);
        assertEquals(490f, SlotTimeLabels.clampCentre(600f, 20f, 50f, 500f), 0.001f);
        assertEquals(200f, SlotTimeLabels.clampCentre(200f, 20f, 50f, 500f), 0.001f);
    }

    @Test
    public void labelIsCentredWhenTheVisibleRangeIsShorterThanIt() {
        assertEquals(52.5f, SlotTimeLabels.clampCentre(0f, 20f, 50f, 55f), 0.001f);
    }

    @Test
    public void chipTextIsWhiteOnDarkAndBlackOnLight() {
        assertEquals(0xFFFFFFFF, SlotTimeLabels.textOn(0xFF000000));
        assertEquals(0xFF000000, SlotTimeLabels.textOn(0xFFFFFFFF));
        assertEquals(0xFF000000, SlotTimeLabels.textOn(0xFFFFEB3B));
        assertEquals(0xFFFFFFFF, SlotTimeLabels.textOn(0xFF1A237E));
    }

    @Test
    public void labelCentresUseTheSameMappingAsTheBlockEdges() {
        // The block edges and the chips both come from minuteToY, so a chip sits on its edge.
        float start = DayGridGeometry.minuteToY(540f, 60f, 1f);
        float end = DayGridGeometry.minuteToY(600f, 60f, 1f);
        float[] c = SlotTimeLabels.centres(start, end, 20f);
        assertEquals(start, c[0], 0.001f);
        assertEquals(end, c[1], 0.001f);
    }
}
