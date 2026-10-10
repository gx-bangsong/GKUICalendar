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
import androidx.appsearch.app.AppSearchSchema
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import androidx.appsearch.platformstorage.PlatformStorage
import com.google.common.util.concurrent.ListenableFuture

private const val TAG = "CalendarAppSearchIndexer"
private const val DATABASE_NAME = "ws.xsoh.etar.events"

private const val PROP_ID = "id"
private const val PROP_NAMESPACE = "namespace"
private const val PROP_TITLE = "title"
private const val PROP_DESCRIPTION = "description"
private const val PROP_LOCATION = "location"
private const val PROP_START_MILLIS = "startMillis"
private const val PROP_END_MILLIS = "endMillis"
private const val PROP_ALL_DAY = "allDay"

/**
 * Entry point for the AppSearch-based indexing integration.
 *
 * Prior commits wired in:
 *   - androidx-appsearch:1.0.0-alpha04 runtime artifact
 *   - appsearch-platform-storage and appsearch-local-storage backends
 *
 * This commit adds [buildInitialSchemaRequest], which constructs the
 * CalendarEventDocument schema via [AppSearchSchema.Builder.addProperty]
 * rather than the @Document annotation processor. The annotation
 * processor (appsearch-compiler) is intentionally not in the kapt
 * pipeline because alpha04's annotation processor cross-talks with
 * the existing Room compiler and causes the gradle-generateBp step
 * to rewrite app/Android.bp on every build.
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

    /**
     * Builds the AppSearchSchema for [CalendarEventDocument] by hand.
     *
     * The @Document annotation processor would normally generate
     * this class from data-class property metadata, but using it
     * here would require appsearch-compiler on kapt, which doesn't
     * coexist cleanly with the Room compiler in this module.
     *
     * The shape mirrors CalendarEventDocument:
     *   - id, namespace, title, description, location are exact-match
     *     String properties (default tokenization).
     *   - startMillis, endMillis are Long properties.
     *   - allDay is a Boolean property.
     */
    private fun buildEventSchema(): AppSearchSchema {
        return AppSearchSchema.Builder(EVENTS_SCHEMA_TYPE)
            .addProperty(
                AppSearchSchema.StringPropertyConfig.Builder(PROP_ID)
                    .setIndexingType(
                        AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS
                    )
                    .build()
            )
            .addProperty(
                AppSearchSchema.StringPropertyConfig.Builder(PROP_NAMESPACE)
                    .setIndexingType(
                        AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS
                    )
                    .build()
            )
            .addProperty(
                AppSearchSchema.StringPropertyConfig.Builder(PROP_TITLE)
                    .setIndexingType(
                        AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS
                    )
                    .build()
            )
            .addProperty(
                AppSearchSchema.StringPropertyConfig.Builder(PROP_DESCRIPTION)
                    .setIndexingType(
                        AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS
                    )
                    .build()
            )
            .addProperty(
                AppSearchSchema.StringPropertyConfig.Builder(PROP_LOCATION)
                    .setIndexingType(
                        AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS
                    )
                    .build()
            )
            .addProperty(AppSearchSchema.LongPropertyConfig.Builder(PROP_START_MILLIS).build())
            .addProperty(AppSearchSchema.LongPropertyConfig.Builder(PROP_END_MILLIS).build())
            .addProperty(AppSearchSchema.BooleanPropertyConfig.Builder(PROP_ALL_DAY).build())
            .build()
    }

    /**
     * Returns a fresh [SetSchemaRequest] registering the
     * CalendarEventDocument schema. The caller is expected to apply
     * it to the [AppSearchSession] returned by [openSession] before
     * writing any documents.
     */
    @JvmStatic
    fun buildInitialSchemaRequest(): SetSchemaRequest =
        SetSchemaRequest.Builder()
            .addSchemas(buildEventSchema())
            .build()
}
