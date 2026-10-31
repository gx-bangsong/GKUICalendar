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
 * Pure routing and state rules for the full-screen time zone page. They have no Android
 * dependency, so JVM tests can cover them. The Fragment only asks these questions and performs
 * the matching UI action.
 */
public final class TimeZoneSearchRouting {

    /** What one back press does on the time zone page. */
    public enum BackAction {
        /** The keyboard is visible: hide it and keep the page, query and results. */
        HIDE_KEYBOARD,
        /** Leave the page without a result, so the original zone is kept. */
        CLOSE_WITHOUT_RESULT
    }

    private TimeZoneSearchRouting() {
    }

    /**
     * One rule for back, whether the system or the app delivers it. While the keyboard is shown,
     * back only hides the keyboard. Once it is hidden, back leaves the page.
     */
    public static BackAction onBack(boolean imeVisible) {
        return imeVisible ? BackAction.HIDE_KEYBOARD : BackAction.CLOSE_WITHOUT_RESULT;
    }

    /** A result is published at most once per page instance, and only while it is attached. */
    public static boolean canDeliverResult(boolean alreadyDelivered, boolean attached) {
        return !alreadyDelivered && attached;
    }

    /** The zone list is read once per page instance, and only if no read has been started. */
    public static boolean shouldStartLoad(boolean loaded, boolean loadRequested) {
        return !loaded && !loadRequested;
    }

    /**
     * A restored first-visible position is applied only after the list is loaded and really has
     * a row at that position. Before that it stays pending, so an early empty list cannot drop it.
     */
    public static boolean canApplyPendingPosition(boolean loaded, int rowCount, int position) {
        return loaded && position >= 0 && position < rowCount;
    }
}
