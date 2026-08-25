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

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.localstorage.LocalStorage
import androidx.appsearch.platformstorage.PlatformStorage
import com.google.common.util.concurrent.ListenableFuture

private const val TAG = "CalendarAppSearchIndexer"
private const val DATABASE_NAME = "ws.xsoh.etar.events"

/**
 * Entry point for the AppSearch-based indexing integration.
 *
 * The three commits preceding this file added:
 *   - androidx-appsearch:1.0.0-alpha04 runtime artifact
 *   - appsearch-platform-storage and appsearch-local-storage backends
 *
 * This commit adds the alpha04 call sites that CalendarApplication
 * will eventually drive. CalendarEventDocument lands in a later
 * commit, paired with the appsearch-compiler kapt processor that
 * reads its @Document annotation.
 */
internal object CalendarAppSearchIndexer {

    /** Logical schema type for indexed calendar events. */
    const val EVENTS_SCHEMA_TYPE = "CalendarEvent"

    /** Logical namespace used when indexing. */
    const val EVENTS_NAMESPACE = "ws.xsoh.etar.events"

    /**
     * True on devices where PlatformStorage is available. LocalStorage
     * is available on every supported Android version so it is the
     * fallback everywhere else.
     */
    @JvmStatic
    fun isPlatformStorageSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Opens an AppSearch session for the Etar events database.
     *
     * PlatformStorage is the Android 12+ central index that feeds
     * Pixel Launcher's search box. On older releases we fall back to
     * LocalStorage, which is in-app private and never reaches the
     * system surface.
     */
    @JvmStatic
    fun openSession(context: Context): ListenableFuture<AppSearchSession> {
        return if (isPlatformStorageSupported()) {
            Log.d(TAG, "Opening PlatformStorage session (Android 12+ central index)")
            PlatformStorage.createSearchSession(
                PlatformStorage.SearchContext.Builder(context, DATABASE_NAME).build()
            )
        } else {
            Log.d(TAG, "Opening LocalStorage session (in-app private index)")
            LocalStorage.createSearchSession(
                LocalStorage.SearchContext.Builder(context, DATABASE_NAME).build()
            )
        }
    }
}
