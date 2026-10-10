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
import android.util.Log
import com.google.common.util.concurrent.Futures

class AppSearchDiagnosticReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.android.calendar.action.RUN_APPSEARCH_DIAG") {
            return
        }

        Log.i("CalAppSearchDiag", "Diagnostic triggered via broadcast")

        val future = CalendarAppSearchDiagnostic.triggerFromBroadcast(context)

        Futures.addCallback(future, object : com.google.common.util.concurrent.FutureCallback<CalendarAppSearchDiagnostic.DiagnosticResult> {
            override fun onSuccess(result: CalendarAppSearchDiagnostic.DiagnosticResult) {
                Log.i("CalAppSearchDiag", "Diagnostic completed: success=${result.success}, message=${result.message}")
            }

            override fun onFailure(t: Throwable) {
                Log.e("CalAppSearchDiag", "Diagnostic failed with exception", t)
            }
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    }

    companion object {
        const val ACTION_RUN_DIAGNOSTIC = "com.android.calendar.action.RUN_APPSEARCH_DIAG"
    }
}
