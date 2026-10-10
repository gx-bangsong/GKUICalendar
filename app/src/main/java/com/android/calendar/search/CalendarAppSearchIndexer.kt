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
import androidx.appsearch.app.Features
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.platformstorage.PlatformStorage
import com.google.common.util.concurrent.ListenableFuture

/**
 * Entry point for the AppSearch PlatformStorage (Android 12+ system index) integration.
 *
 * Uses the hand-written AppSearchSchema.Builder API (no annotation processor).
 * On API < 31 indexing is skipped; the in-app search (SearchManager + CalendarContract)
 * is unaffected.
 */
internal object CalendarAppSearchIndexer {

    const val LOG_TAG = "CalendarAppSearchIndexer"

    /** AppSearch database name for this app's calendar index. */
    const val DATABASE_NAME = "ws.xsoh.etar.events"

    /** Schema type for indexed calendar events. */
    const val EVENTS_SCHEMA_TYPE = "CalendarEvent"

    /** Namespace for indexed calendar event documents. */
    const val EVENTS_NAMESPACE = "ws.xsoh.etar.events"

    // Property names. Document id/namespace/schemaType are GenericDocument metadata
    // and are deliberately NOT modelled as schema properties.
    const val PROP_EVENT_ID = "eventId"
    const val PROP_TITLE = "title"
    const val PROP_DESCRIPTION = "description"
    const val PROP_LOCATION = "location"
    const val PROP_START_MILLIS = "startMillis"
    const val PROP_END_MILLIS = "endMillis"
    const val PROP_ALL_DAY = "allDay"

    /** True on devices where PlatformStorage is available (Android 12+). */
    @JvmStatic
    fun isPlatformStorageSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Opens an AppSearch session on the PlatformStorage backend.
     * On API < 31 returns a failed future and does not open anything.
     */
    @JvmStatic
    fun openSession(context: Context): ListenableFuture<AppSearchSession> {
        return if (isPlatformStorageSupported()) {
            Log.d(LOG_TAG, "open session: backend=PlatformStorage db=$DATABASE_NAME")
            PlatformStorage.createSearchSessionAsync(
                PlatformStorage.SearchContext.Builder(context, DATABASE_NAME).build()
            )
        } else {
            Log.d(LOG_TAG, "skip: PlatformStorage requires API 31+ (sdk=${Build.VERSION.SDK_INT})")
            AppSearchFutures.failedSessionFuture()
        }
    }

    /**
     * Builds the CalendarEvent schema.
     *
     * Every indexed string property MUST declare a tokenizer; an indexingType
     * without a tokenizer is rejected by AppSearch. Title/description/location use
     * PLAIN tokenization with PREFIXES so partial title queries match.
     */
    private fun buildEventSchema(): AppSearchSchema {
        return AppSearchSchema.Builder(EVENTS_SCHEMA_TYPE)
            .addProperty(AppSearchSchema.LongPropertyConfig.Builder(PROP_EVENT_ID).build())
            .addProperty(prefixedText(PROP_TITLE))
            .addProperty(prefixedText(PROP_DESCRIPTION))
            .addProperty(prefixedText(PROP_LOCATION))
            .addProperty(AppSearchSchema.LongPropertyConfig.Builder(PROP_START_MILLIS).build())
            .addProperty(AppSearchSchema.LongPropertyConfig.Builder(PROP_END_MILLIS).build())
            .addProperty(AppSearchSchema.BooleanPropertyConfig.Builder(PROP_ALL_DAY).build())
            .build()
    }

    private fun prefixedText(name: String): AppSearchSchema.StringPropertyConfig =
        AppSearchSchema.StringPropertyConfig.Builder(name)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
            .build()

    /**
     * Returns a SetSchemaRequest for the CalendarEvent schema.
     * forceOverride is NOT set: incompatible changes fail instead of wiping the index.
     *
     * When the session supports ADD_PERMISSIONS_AND_GET_VISIBILITY (AppSearch 1.1.0+ on
     * Android 13+), the schema is readable by the HOME role holder via
     * android.permission.READ_HOME_APP_SEARCH_DATA. No other app is granted access, and
     * no visibility is granted to READ_CALENDAR or any package.
     */
    @JvmStatic
    fun buildSchemaRequest(features: Features): SetSchemaRequest {
        val builder = SetSchemaRequest.Builder().addSchemas(buildEventSchema())
        if (features.isFeatureSupported(Features.ADD_PERMISSIONS_AND_GET_VISIBILITY)) {
            builder.addRequiredPermissionsForSchemaTypeVisibility(
                EVENTS_SCHEMA_TYPE,
                setOf(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA),
            )
        }
        return builder.build()
    }
}
