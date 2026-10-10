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
import android.content.Context.MODE_PRIVATE
import android.os.Build
import android.provider.CalendarContract.Events
import android.util.Log
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.Features
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.RemoveByDocumentIdRequest
import androidx.appsearch.app.SearchSpec
import com.android.calendar.Utils
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Title of the manually created probe event. Only events with this exact title are touched. */
private const val PROBE_TITLE = "ZXCalProbe2026"
private const val PREFS_NAME = "calendar_appsearch_diag"
private const val PREFS_KEY_INDEXED_IDS = "indexed_event_ids"

/** FULL: write the probe event, verify schema/write/search/reopen. SYNC: reconcile with provider. */
internal enum class DiagMode { FULL, SYNC }

/**
 * Debug-only diagnostic for the PlatformStorage contributor side (phase 1).
 * Reads and contributes ONLY events whose title equals [PROBE_TITLE].
 * Logs under tag [CalendarAppSearchIndexer.LOG_TAG]; never logs titles, descriptions or accounts.
 */
internal class CalendarAppSearchDiagnostic(private val context: Context) {

    suspend fun run(mode: DiagMode) {
        log("run mode=$mode pkg=${context.packageName} backend=PlatformStorage " +
            "db=${CalendarAppSearchIndexer.DATABASE_NAME} sdk=${Build.VERSION.SDK_INT}")
        if (!CalendarAppSearchIndexer.isPlatformStorageSupported()) {
            log("skip: PlatformStorage requires API 31+")
            return
        }
        if (!Utils.isCalendarPermissionGranted(context, true)) {
            log("skip: READ_CALENDAR not granted")
            return
        }
        val events = queryProbeEvents()
        log("provider: probeEvents=${events.size} eventIds=${events.map { it.eventId }}")
        when (mode) {
            DiagMode.FULL -> runFull(events)
            DiagMode.SYNC -> runSync(events)
        }
    }

    private suspend fun runFull(events: List<CalendarEventDocument>) {
        if (events.isEmpty()) {
            log("skip: no non-deleted provider event titled as probe; create it in the calendar first")
            return
        }
        val expectedIds = events.map { it.documentId() }.toSet()

        val first = CalendarAppSearchIndexer.openSession(context).await()
        log("session#1 opened")
        try {
            setSchemaAndReadBack(first)
            // Write twice on purpose: a repeated write must not create a second document.
            putDocuments(first, events, attempts = 2)
            verifySearch("session#1", search(first), expectedIds)
        } finally {
            first.close()
            log("session#1 closed")
        }

        val second = CalendarAppSearchIndexer.openSession(context).await()
        log("session#2 reopened")
        try {
            verifySearch("session#2 (reopened)", search(second), expectedIds)
        } finally {
            second.close()
            log("session#2 closed")
        }
        saveIndexedIds(expectedIds)
    }

    private suspend fun runSync(events: List<CalendarEventDocument>) {
        val currentIds = events.map { it.documentId() }.toSet()
        val previousIds = loadIndexedIds()
        val toRemove = previousIds - currentIds
        log("sync: previouslyIndexed=${previousIds.size} currentProvider=${currentIds.size} toRemove=${toRemove.size}")

        val session = CalendarAppSearchIndexer.openSession(context).await()
        try {
            setSchemaAndReadBack(session)
            if (events.isNotEmpty()) {
                putDocuments(session, events, attempts = 1)
            }
            if (toRemove.isNotEmpty()) {
                val result = session.removeAsync(
                    RemoveByDocumentIdRequest.Builder(CalendarAppSearchIndexer.EVENTS_NAMESPACE)
                        .addIds(toRemove)
                        .build()
                ).await()
                log("remove: successes=${result.successes.size} failures=${result.failures.size}")
                result.failures.forEach { (id, r) -> log("remove failure id=$id result=$r") }
            }
            val hits = search(session)
            verifySearch("sync-search", hits, currentIds)
            val staleHits = hits.map { it.id }.filter { it in toRemove }
            log("sync: staleHitsForRemovedEvents=${staleHits.size} (expected 0)")
        } finally {
            session.close()
        }
        saveIndexedIds(currentIds)
    }

    private suspend fun setSchemaAndReadBack(session: AppSearchSession) {
        val hasHomeGrant = session.features.isFeatureSupported(Features.ADD_PERMISSIONS_AND_GET_VISIBILITY)
        log("features: ADD_PERMISSIONS_AND_GET_VISIBILITY=$hasHomeGrant")
        val setResponse = session.setSchemaAsync(
            CalendarAppSearchIndexer.buildSchemaRequest(session.features)
        ).await()
        log("schema: setSchema completed response=$setResponse")
        val readBack = session.getSchemaAsync().await()
        val schemaTypes = readBack.schemas.map { it.schemaType }
        log("schema readback: types=$schemaTypes " +
            "calendarEventRegistered=${CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE in schemaTypes}")
        // Permission sets are reported only as the constant ids (e.g. [5] = HOME), never document data.
        log("schema readback: homeGrantPermissions=" +
            "${readBack.requiredPermissionsForSchemaTypeVisibility[CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE]}")
    }

    private suspend fun putDocuments(
        session: AppSearchSession,
        events: List<CalendarEventDocument>,
        attempts: Int,
    ) {
        val docs: List<GenericDocument> = events.map { it.toGenericDocument() }
        repeat(attempts) { index ->
            val result = session.putAsync(
                PutDocumentsRequest.Builder().addGenericDocuments(docs).build()
            ).await()
            log("put attempt=${index + 1} docs=${docs.size} successes=${result.successes.size} " +
                "failures=${result.failures.size}")
            result.failures.forEach { (id, r) -> log("put failure id=$id result=$r") }
        }
    }

    private suspend fun search(session: AppSearchSession): List<GenericDocument> {
        val spec = SearchSpec.Builder()
            .addFilterSchemas(CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE)
            .setResultCountPerPage(20)
            .build()
        val results = session.search(PROBE_TITLE, spec)
        try {
            return results.getNextPageAsync().await().map { it.genericDocument }
        } finally {
            results.close()
        }
    }

    private fun verifySearch(label: String, hits: List<GenericDocument>, expectedIds: Set<String>) {
        val distinctIds = hits.map { it.id }.toSet()
        val matched = expectedIds.count { it in distinctIds }
        val hitEventIds = hits.map { it.getPropertyLong(CalendarAppSearchIndexer.PROP_EVENT_ID) }
        log("$label: hits=${hits.size} distinctDocs=${distinctIds.size} " +
            "expectedMatched=$matched/${expectedIds.size} hitEventIds=$hitEventIds")
        if (hits.size != distinctIds.size) {
            log("$label: DUPLICATE documents detected")
        }
    }

    private fun queryProbeEvents(): List<CalendarEventDocument> {
        val projection = arrayOf(
            Events._ID,
            Events.TITLE,
            Events.DESCRIPTION,
            Events.EVENT_LOCATION,
            Events.DTSTART,
            Events.DTEND,
            Events.ALL_DAY,
        )
        val selection = "${Events.TITLE}=? AND ${Events.DELETED}=0"
        val out = mutableListOf<CalendarEventDocument>()
        context.contentResolver.query(
            Events.CONTENT_URI, projection, selection, arrayOf(PROBE_TITLE), null
        )?.use { cursor ->
            val idxId = cursor.getColumnIndexOrThrow(Events._ID)
            val idxTitle = cursor.getColumnIndexOrThrow(Events.TITLE)
            val idxDesc = cursor.getColumnIndexOrThrow(Events.DESCRIPTION)
            val idxLoc = cursor.getColumnIndexOrThrow(Events.EVENT_LOCATION)
            val idxStart = cursor.getColumnIndexOrThrow(Events.DTSTART)
            val idxEnd = cursor.getColumnIndexOrThrow(Events.DTEND)
            val idxAllDay = cursor.getColumnIndexOrThrow(Events.ALL_DAY)
            while (cursor.moveToNext()) {
                val start = cursor.getLong(idxStart)
                // DTEND is null for recurring events; fall back to start.
                val end = if (cursor.isNull(idxEnd)) start else cursor.getLong(idxEnd)
                out += CalendarEventDocument(
                    eventId = cursor.getLong(idxId),
                    title = cursor.getString(idxTitle) ?: "",
                    description = cursor.getString(idxDesc) ?: "",
                    location = cursor.getString(idxLoc) ?: "",
                    startMillis = start,
                    endMillis = end,
                    allDay = cursor.getInt(idxAllDay) == 1,
                )
            }
        }
        return out
    }

    private fun loadIndexedIds(): Set<String> =
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getStringSet(PREFS_KEY_INDEXED_IDS, emptySet()) ?: emptySet()

    private fun saveIndexedIds(ids: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putStringSet(PREFS_KEY_INDEXED_IDS, ids).apply()
    }

    private fun log(message: String) {
        Log.i(CalendarAppSearchIndexer.LOG_TAG, "diag: $message")
    }
}

/** Bridges a Guava future to a suspending call without blocking the calling thread. */
internal suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (t: Throwable) {
                cont.resumeWithException(t.cause ?: t)
            }
        }, Executor { it.run() })
    }
