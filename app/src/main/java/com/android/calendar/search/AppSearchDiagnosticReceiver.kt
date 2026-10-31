/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.calendar.search

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only trigger for [CalendarAppSearchDiagnostic]. Ignored in non-debuggable builds.
 *
 *   adb shell am broadcast -a com.android.calendar.action.RUN_APPSEARCH_DIAG
 *   adb shell am broadcast -a com.android.calendar.action.RUN_APPSEARCH_DIAG --es mode sync
 *   adb shell am broadcast -a com.android.calendar.action.RUN_APPSEARCH_DIAG --es mode consumers  (read-only)
 *   Modes: absent or "full" -> FULL, "sync", "consumers". Any other value is rejected without action.
 *
 * Visibility experiments (debug only; default is on/on, the shipped configuration):
 *   --es home off        do not request READ_HOME_APP_SEARCH_DATA on the probe schema
 *   --es displayed off   setSchemaTypeDisplayedBySystem(CalendarEvent, false)
 * Every run re-applies the policy, so the last run defines the device state.
 */
class AppSearchDiagnosticReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_DIAGNOSTIC) return
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Log.w(CalendarAppSearchIndexer.LOG_TAG, "diag ignored: build is not debuggable")
            return
        }
        // Strict parsing: an unknown or malformed extra aborts before any work or write.
        val rawMode = intent.getStringExtra("mode")
        val mode = if (intent.hasExtra("mode") && rawMode == null) null else parseDiagMode(rawMode)
        if (mode == null) {
            Log.e(CalendarAppSearchIndexer.LOG_TAG, "diag rejected: unknown mode; no action taken")
            return
        }
        val homeExtra = intent.getStringExtra("home")
        val displayedExtra = intent.getStringExtra("displayed")
        if (!isOnOff(homeExtra) || !isOnOff(displayedExtra)) {
            Log.e(CalendarAppSearchIndexer.LOG_TAG, "diag rejected: home/displayed must be on, off or absent; no action taken")
            return
        }
        val policy = CalendarAppSearchIndexer.VisibilityPolicy(
            homeRoleRead = homeExtra != "off",
            displayedBySystem = displayedExtra != "off",
        )
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CalendarAppSearchDiagnostic(appContext).run(mode, policy)
            } catch (t: Throwable) {
                Log.e(CalendarAppSearchIndexer.LOG_TAG, "diag failed: ${t.javaClass.name}", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN_DIAGNOSTIC = "com.android.calendar.action.RUN_APPSEARCH_DIAG"

        /** Absent, "on" or "off" only; anything else is rejected before any work. */
        private fun isOnOff(value: String?): Boolean = value == null || value == "on" || value == "off"
    }
}
