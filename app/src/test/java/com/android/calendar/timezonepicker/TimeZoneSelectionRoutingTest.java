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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure rules that decide what each time zone result may write. These are logic checks only.
 * Whether the screens really apply the result, keep the draft, and survive rotation needs an
 * on-device run.
 */
public class TimeZoneSelectionRoutingTest {

    private static final String ZONE = "Asia/Shanghai";

    @Test
    public void eventResultOnlyUpdatesTheDraft() {
        assertEquals(TimeZoneSelectionRouting.Action.UPDATE_EVENT_DRAFT,
                TimeZoneSelectionRouting.decide(
                        TimeZoneSelectionRouting.Target.EVENT_DRAFT, ZONE, true, true));
        // The home switch and the permission do not change what the event draft may receive.
        assertEquals(TimeZoneSelectionRouting.Action.UPDATE_EVENT_DRAFT,
                TimeZoneSelectionRouting.decide(
                        TimeZoneSelectionRouting.Target.EVENT_DRAFT, ZONE, false, false));
    }

    @Test
    public void eventResultNeverWritesTheHomeZone() {
        assertNotEquals(TimeZoneSelectionRouting.Action.WRITE_HOME_ZONE,
                TimeZoneSelectionRouting.decide(
                        TimeZoneSelectionRouting.Target.EVENT_DRAFT, ZONE, true, true));
    }

    @Test
    public void settingsResultOnlyWritesTheHomeZoneWhenAllowed() {
        assertEquals(TimeZoneSelectionRouting.Action.WRITE_HOME_ZONE,
                TimeZoneSelectionRouting.decide(
                        TimeZoneSelectionRouting.Target.HOME_PREFERENCE, ZONE, true, true));
    }

    @Test
    public void settingsResultNeverUpdatesTheEventDraft() {
        assertNotEquals(TimeZoneSelectionRouting.Action.UPDATE_EVENT_DRAFT,
                TimeZoneSelectionRouting.decide(
                        TimeZoneSelectionRouting.Target.HOME_PREFERENCE, ZONE, true, true));
    }

    @Test
    public void settingsResultIsIgnoredWhenTheSwitchIsOffOrPermissionIsMissing() {
        assertEquals(TimeZoneSelectionRouting.Action.NONE,
                TimeZoneSelectionRouting.decide(
                        TimeZoneSelectionRouting.Target.HOME_PREFERENCE, ZONE, false, true));
        assertEquals(TimeZoneSelectionRouting.Action.NONE,
                TimeZoneSelectionRouting.decide(
                        TimeZoneSelectionRouting.Target.HOME_PREFERENCE, ZONE, true, false));
    }

    @Test
    public void cancelWritesNothingForEitherTarget() {
        // Cancel delivers no zone ID, so null must never reach a write.
        for (TimeZoneSelectionRouting.Target target : TimeZoneSelectionRouting.Target.values()) {
            assertEquals(TimeZoneSelectionRouting.Action.NONE,
                    TimeZoneSelectionRouting.decide(target, null, true, true));
            assertEquals(TimeZoneSelectionRouting.Action.NONE,
                    TimeZoneSelectionRouting.decide(target, "", true, true));
        }
    }

    @Test
    public void unknownZoneIdWritesNothingForEitherTarget() {
        for (TimeZoneSelectionRouting.Target target : TimeZoneSelectionRouting.Target.values()) {
            assertEquals(TimeZoneSelectionRouting.Action.NONE,
                    TimeZoneSelectionRouting.decide(target, "Mars/Olympus_Mons", true, true));
        }
    }

    @Test
    public void missingTargetWritesNothing() {
        assertEquals(TimeZoneSelectionRouting.Action.NONE,
                TimeZoneSelectionRouting.decide(null, ZONE, true, true));
    }

    @Test
    public void requestKeysAreDistinctAndEachMapsToOneTarget() {
        assertNotEquals(TimeZoneSelectionRouting.REQUEST_KEY_EVENT,
                TimeZoneSelectionRouting.REQUEST_KEY_HOME);
        assertEquals(TimeZoneSelectionRouting.Target.EVENT_DRAFT,
                TimeZoneSelectionRouting.targetFor(TimeZoneSelectionRouting.REQUEST_KEY_EVENT));
        assertEquals(TimeZoneSelectionRouting.Target.HOME_PREFERENCE,
                TimeZoneSelectionRouting.targetFor(TimeZoneSelectionRouting.REQUEST_KEY_HOME));
    }

    @Test
    public void unownedRequestKeysAreNotRouted() {
        assertNull(TimeZoneSelectionRouting.targetFor(null));
        assertNull(TimeZoneSelectionRouting.targetFor(""));
        assertNull(TimeZoneSelectionRouting.targetFor("com.android.calendar.timezonepicker.RESULT"));
    }

    @Test
    public void knownZoneCheckRejectsUnknownAndEmptyIds() {
        assertTrue(TimeZoneSelectionRouting.isKnownZoneId("Europe/Stockholm"));
        assertFalse(TimeZoneSelectionRouting.isKnownZoneId("Mars/Olympus_Mons"));
        assertFalse(TimeZoneSelectionRouting.isKnownZoneId(""));
        assertFalse(TimeZoneSelectionRouting.isKnownZoneId(null));
    }
}
