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
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract.Events
import android.util.Log
import com.android.calendar.Utils
import androidx.appsearch.app.AppSearchSchema
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.SearchResult
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.app.SetSchemaResponse
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

private const val TAG = "CalAppSearchDiag"
private const val TEST_EVENT_TITLE = "ZXCalProbe2026"
private const val INDEXING_SCOPE_DAYS = 365

/**
 * Diagnostic entry point for AppSearch PlatformStorage indexing.
 * Only indexes the test event "ZXCalProbe2026" by default.
 * Call from a debug tile, ADB shell, or unit test.
 */
class CalendarAppSearchDiagnostic private constructor(
    private val context: Context,
    private val executor: ListeningExecutorService
) {

    fun runDiagnostic(): ListenableFuture<DiagnosticResult> {
        return if (!CalendarAppSearchIndexer.isPlatformStorageSupported()) {
            Log.w(TAG, "PlatformStorage not supported (API < 31)")
            Futures.immediateFuture(DiagnosticResult(
                success = false,
                message = "PlatformStorage requires Android 12+ (API 31)"
            ))
        } else if (!Utils.isCalendarPermissionGranted(context, true)) {
            Log.w(TAG, "READ_CALENDAR permission not granted")
            Futures.immediateFuture(DiagnosticResult(
                success = false,
                message = "READ_CALENDAR permission not granted"
            ))
        } else {
            Futures.transformAsync(
                CalendarAppSearchIndexer.openSession(context),
                { session -> runDiagnosticWithSession(session) },
                executor
            )
        }
    }

    private fun runDiagnosticWithSession(session: AppSearchSession): ListenableFuture<DiagnosticResult> {
        return Futures.transformAsync(
            setSchema(session),
            { setSchemaResponse ->
                if (!setSchemaResponse.succeeded) {
                    Log.e(TAG, "Schema registration failed: ${setSchemaResponse.errorMessage}")
                    closeSession(session)
                    Futures.immediateFuture(DiagnosticResult(
                        success = false,
                        message = "Schema registration failed: ${setSchemaResponse.errorMessage ?: "unknown"}"
                    ))
                } else {
                    Log.d(TAG, "Schema registered successfully, reading back...")
                    Futures.transformAsync(
                        readBackSchema(session),
                        { schema ->
                            verifySchema(schema)
                            Futures.transformAsync(
                                findAndIndexTestEvent(session),
                                { indexResult ->
                                    Futures.transformAsync(
                                        searchTestEvent(session),
                                        { searchResult ->
                                            closeSession(session)
                                            Futures.transformAsync(
                                                CalendarAppSearchIndexer.openSession(context),
                                                { newSession ->
                                                    Futures.transformAsync(
                                                        searchTestEvent(newSession),
                                                        { reopenedSearchResult ->
                                                            closeSession(newSession)
                                                            buildFinalResult(indexResult, searchResult, reopenedSearchResult)
                                                        },
                                                        executor
                                                    )
                                                },
                                                executor
                                            )
                                        },
                                        executor
                                    )
                                },
                                executor
                            )
                        },
                        executor
                    )
                }
            },
            executor
        )
    }

    private fun setSchema(session: AppSearchSession): ListenableFuture<SetSchemaResponse> {
        val request = CalendarAppSearchIndexer.buildInitialSchemaRequest()
        return Futures.transform(
            session.setSchema(request),
            { it },
            executor
        )
    }

    private fun readBackSchema(session: AppSearchSession): ListenableFuture<AppSearchSchema?> {
        return Futures.transformAsync(
            session.getSchema(CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE),
            { schema ->
                if (schema != null) {
                    Log.d(TAG, "Read back schema: ${schema.schemaType}, properties: ${schema.propertyConfigMap?.size ?: 0}")
                } else {
                    Log.w(TAG, "Schema not found after registration")
                }
                Futures.immediateFuture(schema)
            },
            executor
        )
    }

    private fun verifySchema(schema: AppSearchSchema?) {
        if (schema == null) {
            Log.e(TAG, "Schema verification failed: schema is null")
            return
        }
        val expectedProps = setOf("id", "namespace", "title", "description", "location", "startMillis", "endMillis", "allDay")
        val actualProps = schema.propertyConfigMap?.keys?.toSet() ?: emptySet()
        val missing = expectedProps - actualProps
        if (missing.isNotEmpty()) {
            Log.e(TAG, "Schema missing properties: $missing")
        } else {
            Log.d(TAG, "Schema verification passed: all ${expectedProps.size} properties present")
        }
    }

    private fun findAndIndexTestEvent(session: AppSearchSession): ListenableFuture<IndexResult> {
        val cursor = findTestEventCursor()
        return if (cursor == null || cursor.count == 0) {
            Log.w(TAG, "Test event '$TEST_EVENT_TITLE' not found in CalendarProvider")
            Futures.immediateFuture(IndexResult(0, 0, "Test event not found"))
        } else {
            val docs = mutableListOf<GenericDocument>()
            try {
                while (cursor.moveToNext()) {
                    val doc = buildDocumentFromCursor(cursor)
                    docs.add(doc)
                    Log.d(TAG, "Built document for event: id=${doc.id}, title=${doc.getPropertyString("title")}")
                }
            } finally {
                cursor.close()
            }

            if (docs.isEmpty()) {
                Futures.immediateFuture(IndexResult(0, 0, "No documents built"))
            } else {
                val futures = docs.map { doc ->
                    Futures.transformAsync(
                        session.put(doc),
                        { success ->
                            Log.d(TAG, "Put result for ${doc.id}: success=$success")
                            IndexResult(if (success) 1 else 0, if (success) 0 else 1, doc.id)
                        },
                        executor
                    )
                }
                Futures.transform(
                    Futures.allAsList(*futures.toTypedArray()),
                    { results ->
                        val success = results.count { it.successes > 0 }
                        val failure = results.count { it.failures > 0 }
                        val ids = results.map { it.documentId }.joinToString(", ")
                        IndexResult(success, failure, ids)
                    },
                    executor
                )
            }
        }
    }

    private fun findTestEventCursor(): Cursor? {
        val now = System.currentTimeMillis()
        val start = now - (INDEXING_SCOPE_DAYS * 24 * 60 * 60 * 1000L)
        val end = now + (INDEXING_SCOPE_DAYS * 24 * 60 * 60 * 1000L)

        val projection = arrayOf(
            Events._ID,
            Events.TITLE,
            Events.DESCRIPTION,
            Events.EVENT_LOCATION,
            Events.DTSTART,
            Events.DTEND,
            Events.ALL_DAY,
            Events.EVENT_TIMEZONE,
            Events.RRULE,
            Events.STATUS,
            Events.CALENDAR_ID,
            Events.CUSTOM_APP_URI
        )

        val selection = "${Events.VISIBLE}=1 AND ${Events.TITLE}=? AND ${Events.DTSTART} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(TEST_EVENT_TITLE, start.toString(), end.toString())

        return try {
            context.contentResolver.query(
                Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error querying test event", e)
            null
        }
    }

    private fun buildDocumentFromCursor(cursor: Cursor): GenericDocument {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(Events._ID))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(Events.TITLE)) ?: ""
        val description = cursor.getString(cursor.getColumnIndexOrThrow(Events.DESCRIPTION)) ?: ""
        val location = cursor.getString(cursor.getColumnIndexOrThrow(Events.EVENT_LOCATION)) ?: ""
        val dtstart = cursor.getLong(cursor.getColumnIndexOrThrow(Events.DTSTART))
        val dtend = cursor.getLong(cursor.getColumnIndexOrThrow(Events.DTEND))
        val allDay = cursor.getInt(cursor.getColumnIndexOrThrow(Events.ALL_DAY)) == 1
        val calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(Events.CALENDAR_ID))
        val eventTimezone = cursor.getString(cursor.getColumnIndexOrThrow(Events.EVENT_TIMEZONE)) ?: "UTC"
        val rrule = cursor.getString(cursor.getColumnIndexOrThrow(Events.RRULE))
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(Events.STATUS))
        val customAppUri = cursor.getString(cursor.getColumnIndexOrThrow(Events.CUSTOM_APP_URI))

        val docId = "${context.packageName}#$calendarId#$id"

        val builder = GenericDocument.Builder(docId, CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE)
            .setNamespace(CalendarAppSearchIndexer.EVENTS_NAMESPACE)
            .putString(CalendarAppSearchIndexer.PROP_ID, id.toString())
            .putString(CalendarAppSearchIndexer.PROP_NAMESPACE, CalendarAppSearchIndexer.EVENTS_NAMESPACE)
            .putString(CalendarAppSearchIndexer.PROP_TITLE, title)
            .putString(CalendarAppSearchIndexer.PROP_DESCRIPTION, description)
            .putString(CalendarAppSearchIndexer.PROP_LOCATION, location)
            .putLong(CalendarAppSearchIndexer.PROP_START_MILLIS, dtstart)
            .putLong(CalendarAppSearchIndexer.PROP_END_MILLIS, dtend)
            .putBoolean(CalendarAppSearchIndexer.PROP_ALL_DAY, allDay)

        return builder.build()
    }

    private fun searchTestEvent(session: AppSearchSession): ListenableFuture<SearchResult> {
        val spec = SearchSpec.Builder()
            .addFilterSchemas(CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE)
            .setResultCountPerPage(10)
            .build()

        return Futures.transformAsync(
            session.search(TEST_EVENT_TITLE, spec),
            { it },
            executor
        )
    }

    private fun closeSession(session: AppSearchSession?) {
        if (session != null) {
            try {
                session.close()
                Log.d(TAG, "Session closed")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing session", e)
            }
        }
    }

    private fun buildFinalResult(
        indexResult: IndexResult,
        searchResult: SearchResult,
        reopenedSearchResult: SearchResult
    ): ListenableFuture<DiagnosticResult> {
        val hitCount = searchResult.matchInfos?.size ?: 0
        val reopenedHitCount = reopenedSearchResult.matchInfos?.size ?: 0

        val matchedEventIds = searchResult.matchInfos?.map { it.document.id }.joinToString(", ") ?: ""
        val reopenedMatchedIds = reopenedSearchResult.matchInfos?.map { it.document.id }.joinToString(", ") ?: ""

        val success = indexResult.successes > 0 && hitCount > 0 && reopenedHitCount > 0
        val message = if (success) {
            "Diagnostic PASSED: indexed=${indexResult.successes}, searchHits=$hitCount, reopenedHits=$reopenedHitCount, matchedIds=[$matchedEventIds]"
        } else {
            "Diagnostic FAILED: indexed=${indexResult.successes}, failures=${indexResult.failures}, searchHits=$hitCount, reopenedHits=$reopenedHitCount, matchedIds=[$matchedEventIds]"
        }

        Log.i(TAG, message)
        Futures.immediateFuture(DiagnosticResult(success, message))
    }

    companion object {
        fun create(context: Context): CalendarAppSearchDiagnostic {
            val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
            return CalendarAppSearchDiagnostic(context.applicationContext, executor)
        }

        fun triggerFromBroadcast(context: Context): ListenableFuture<DiagnosticResult> {
            return create(context).runDiagnostic()
        }
    }
}

data class DiagnosticResult(
    val success: Boolean,
    val message: String
)

data class IndexResult(
    val successes: Int,
    val failures: Int,
    val documentIds: String
)
