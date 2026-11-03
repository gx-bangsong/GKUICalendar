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

package com.android.calendar.timezonepicker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure routing rules of the time zone page. These cover decisions only. Real back delivery,
 * keyboard hiding, rotation and result delivery need an on-device run.
 */
public class TimeZoneSearchRoutingTest {

    @Test
    public void backWithKeyboardVisibleOnlyHidesTheKeyboard() {
        assertEquals(TimeZoneSearchRouting.BackAction.HIDE_KEYBOARD,
                TimeZoneSearchRouting.onBack(true));
    }

    @Test
    public void backWithKeyboardHiddenLeavesThePageWithoutResult() {
        assertEquals(TimeZoneSearchRouting.BackAction.CLOSE_WITHOUT_RESULT,
                TimeZoneSearchRouting.onBack(false));
    }

    @Test
    public void resultIsDeliveredOnlyOnceAndOnlyWhileAttached() {
        assertTrue(TimeZoneSearchRouting.canDeliverResult(false, true));
        assertFalse(TimeZoneSearchRouting.canDeliverResult(true, true));
        assertFalse(TimeZoneSearchRouting.canDeliverResult(false, false));
    }

    @Test
    public void zoneListIsLoadedOnlyOncePerPageInstance() {
        assertTrue(TimeZoneSearchRouting.shouldStartLoad(false, false));
        assertFalse(TimeZoneSearchRouting.shouldStartLoad(false, true));
        assertFalse(TimeZoneSearchRouting.shouldStartLoad(true, true));
        assertFalse(TimeZoneSearchRouting.shouldStartLoad(true, false));
    }

    @Test
    public void pendingPositionWaitsForTheLoadedList() {
        // An early empty list (before the load finishes) must not consume the position.
        assertFalse(TimeZoneSearchRouting.canApplyPendingPosition(false, 0, 12));
        assertFalse(TimeZoneSearchRouting.canApplyPendingPosition(false, 40, 12));
    }

    @Test
    public void pendingPositionMustBeInsideTheCurrentRows() {
        assertTrue(TimeZoneSearchRouting.canApplyPendingPosition(true, 40, 12));
        assertFalse(TimeZoneSearchRouting.canApplyPendingPosition(true, 5, 12));
        assertFalse(TimeZoneSearchRouting.canApplyPendingPosition(true, 0, 0));
        assertFalse(TimeZoneSearchRouting.canApplyPendingPosition(true, 40, -1));
    }
}
