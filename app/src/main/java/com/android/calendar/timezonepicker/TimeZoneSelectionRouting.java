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

/**
 * Pure rules that connect one full-screen time zone page to the screen that opened it.
 *
 * <p>The page only returns a real zone ID. Each caller has its own request key, and this class
 * decides what that caller may write. It has no Android dependency, so JVM tests can cover it.
 */
public final class TimeZoneSelectionRouting {

    /** Result key of the event edit form. A result only changes the event draft. */
    public static final String REQUEST_KEY_EVENT =
            "com.android.calendar.timezonepicker.RESULT_EVENT_TIME_ZONE";

    /** Result key of the settings page. A result only changes the home time zone. */
    public static final String REQUEST_KEY_HOME =
            "com.android.calendar.timezonepicker.RESULT_HOME_TIME_ZONE";

    /** Which screen asked for the zone. */
    public enum Target {
        EVENT_DRAFT,
        HOME_PREFERENCE
    }

    /** What the caller may do with a result. */
    public enum Action {
        /** Nothing is written: cancel, unknown ID, or a setting that does not allow it. */
        NONE,
        /** Only the event draft changes. The event is not saved here. */
        UPDATE_EVENT_DRAFT,
        /** Only the stored home time zone changes. */
        WRITE_HOME_ZONE
    }

    private TimeZoneSelectionRouting() {
    }

    /** Maps a request key to its target, or null for a key this app does not own. */
    public static Target targetFor(String requestKey) {
        if (REQUEST_KEY_EVENT.equals(requestKey)) {
            return Target.EVENT_DRAFT;
        }
        if (REQUEST_KEY_HOME.equals(requestKey)) {
            return Target.HOME_PREFERENCE;
        }
        return null;
    }

    /**
     * Decides what a result may change.
     *
     * @param target the screen that opened the page
     * @param zoneId the selected zone ID, or null when the page was cancelled
     * @param homeEnabled whether the home time zone switch is on right now
     * @param permissionGranted whether the calendar permission is granted right now
     */
    public static Action decide(Target target, String zoneId, boolean homeEnabled,
            boolean permissionGranted) {
        if (target == null || !isKnownZoneId(zoneId)) {
            return Action.NONE;
        }
        switch (target) {
            case EVENT_DRAFT:
                return Action.UPDATE_EVENT_DRAFT;
            case HOME_PREFERENCE:
                return homeEnabled && permissionGranted
                        ? Action.WRITE_HOME_ZONE : Action.NONE;
            default:
                return Action.NONE;
        }
    }

    /**
     * True only for a real zone ID the platform knows. An unknown ID is never stored, because
     * TimeZone would turn it into GMT without saying so.
     */
    public static boolean isKnownZoneId(String zoneId) {
        return TimeZoneSearchMatcher.isKnownId(zoneId);
    }
}
