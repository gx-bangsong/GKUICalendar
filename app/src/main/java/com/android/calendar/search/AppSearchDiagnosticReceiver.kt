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
 */
class AppSearchDiagnosticReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_DIAGNOSTIC) return
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Log.w(CalendarAppSearchIndexer.LOG_TAG, "diag ignored: build is not debuggable")
            return
        }
        val mode = if (intent.getStringExtra("mode") == "sync") DiagMode.SYNC else DiagMode.FULL
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CalendarAppSearchDiagnostic(appContext).run(mode)
            } catch (t: Throwable) {
                Log.e(CalendarAppSearchIndexer.LOG_TAG, "diag failed: ${t.javaClass.name}", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN_DIAGNOSTIC = "com.android.calendar.action.RUN_APPSEARCH_DIAG"
    }
}
