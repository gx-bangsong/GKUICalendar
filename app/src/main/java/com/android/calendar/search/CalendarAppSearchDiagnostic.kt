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
import androidx.appsearch.exceptions.AppSearchException
import androidx.appsearch.platformstorage.PlatformStorage
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
internal enum class DiagMode { FULL, SYNC, CONSUMERS }

/**
 * Maps the broadcast `mode` extra. Absent or "full" -> FULL, "sync" -> SYNC, "consumers" -> CONSUMERS.
 * Any other value returns null; the caller must abort without performing any write.
 */
internal fun parseDiagMode(raw: String?): DiagMode? = when (raw) {
    null, "full" -> DiagMode.FULL
    "sync" -> DiagMode.SYNC
    "consumers" -> DiagMode.CONSUMERS
    else -> null
}

/** Outcome of the read-only consumer survey. Decided only by what the query actually returned. */
internal enum class ConsumerSurvey { FOUND, NONE_VISIBLE, ERROR, TRUNCATED }

/** Read-only consumer survey: lists GlobalSearchApplicationInfo metadata only. */
private const val GSAI_SCHEMA = "builtin:GlobalSearchApplicationInfo"
private const val CONSUMER_PAGE_SIZE = 50
private const val CONSUMER_MAX_PAGES = 1000

/**
 * Debug-only diagnostic for the PlatformStorage contributor side (phase 1).
 * Reads and contributes ONLY events whose title equals [PROBE_TITLE].
 * Logs under tag [CalendarAppSearchIndexer.LOG_TAG]; never logs titles, descriptions or accounts.
 */
internal class CalendarAppSearchDiagnostic(private val context: Context) {

    suspend fun run(
        mode: DiagMode,
        policy: CalendarAppSearchIndexer.VisibilityPolicy = CalendarAppSearchIndexer.VisibilityPolicy(),
    ) {
        log("run mode=$mode pkg=${context.packageName} backend=PlatformStorage " +
            "db=${CalendarAppSearchIndexer.DATABASE_NAME} sdk=${Build.VERSION.SDK_INT} requested=[$policy]")
        if (!CalendarAppSearchIndexer.isPlatformStorageSupported()) {
            log("skip: PlatformStorage requires API 31+")
            return
        }
        if (mode == DiagMode.CONSUMERS) {
            runConsumers()
            return
        }
        if (!Utils.isCalendarPermissionGranted(context, true)) {
            log("skip: READ_CALENDAR not granted")
            return
        }
        val events = queryProbeEvents()
        log("provider: probeEvents=${events.size} eventIds=${events.map { it.eventId }}")
        when (mode) {
            DiagMode.FULL -> runFull(events, policy)
            DiagMode.SYNC -> runSync(events, policy)
            DiagMode.CONSUMERS -> Unit
        }
    }

    private suspend fun runFull(events: List<CalendarEventDocument>, policy: CalendarAppSearchIndexer.VisibilityPolicy) {
        if (events.isEmpty()) {
            log("skip: no non-deleted provider event titled as probe; create it in the calendar first")
            return
        }
        val expectedIds = events.map { it.documentId() }.toSet()

        val first = CalendarAppSearchIndexer.openSession(context).await()
        log("session#1 opened")
        try {
            setSchemaAndReadBack(first, policy)
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

    private suspend fun runSync(events: List<CalendarEventDocument>, policy: CalendarAppSearchIndexer.VisibilityPolicy) {
        val currentIds = events.map { it.documentId() }.toSet()
        val previousIds = loadIndexedIds()
        val toRemove = previousIds - currentIds
        log("sync: previouslyIndexed=${previousIds.size} currentProvider=${currentIds.size} toRemove=${toRemove.size}")

        val session = CalendarAppSearchIndexer.openSession(context).await()
        try {
            setSchemaAndReadBack(session, policy)
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

    private suspend fun setSchemaAndReadBack(
        session: AppSearchSession,
        policy: CalendarAppSearchIndexer.VisibilityPolicy,
    ) {
        val hasVisibilityFeature = session.features.isFeatureSupported(Features.ADD_PERMISSIONS_AND_GET_VISIBILITY)
        // Requested != effective: the HOME grant is only *requested* here; the readback below is
        // the only evidence of what the backend stored.
        val homeRequested = policy.homeRoleRead && hasVisibilityFeature
        log("visibility: requested=[$policy] featureSupported=$hasVisibilityFeature homeGrantRequested=$homeRequested")
        val setResponse = session.setSchemaAsync(
            CalendarAppSearchIndexer.buildSchemaRequest(session.features, policy)
        ).await()
        log("schema: setSchema completed response=$setResponse")
        val readBack = session.getSchemaAsync().await()
        val schemaTypes = readBack.schemas.map { it.schemaType }
        log("schema readback: types=$schemaTypes " +
            "calendarEventRegistered=${CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE in schemaTypes}")
        // Feature-guarded readbacks: unsupported backends report "unknown", never a guessed value.
        val homeReadback = if (hasVisibilityFeature) {
            readBack.requiredPermissionsForSchemaTypeVisibility[CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE]
                ?.toString() ?: "none"
        } else {
            "unknown"
        }
        log("schema readback: homeGrantPermissions=$homeReadback (ids: 5=HOME)")
        val displayedReadback = if (hasVisibilityFeature) {
            (CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE !in readBack.schemaTypesNotDisplayedBySystem).toString()
        } else {
            "unknown"
        }
        log("schema readback: displayedBySystem=$displayedReadback")
    }

    /**
     * Read-only survey of builtin:GlobalSearchApplicationInfo documents visible to this package.
     *
     * VERBATIM_SEARCH is recorded as a capability but does NOT short-circuit the survey: the
     * general query (empty query string + schema filter) is always attempted, and the outcome
     * is decided by what that query returns. Logs only owning package, database,
     * applicationType and schemaTypes. Never writes, removes or changes schema.
     */
    private suspend fun runConsumers() {
        var stage = "createSession"
        var pages = 0
        var entries = 0
        var truncated = false
        val session = try {
            PlatformStorage.createGlobalSearchSessionAsync(
                PlatformStorage.GlobalSearchContext.Builder(context).build()
            ).await()
        } catch (t: Throwable) {
            log("consumers: result=${ConsumerSurvey.ERROR} stage=$stage error=${describe(t)}")
            return
        }
        try {
            stage = "features"
            val verbatim = session.features.isFeatureSupported(Features.VERBATIM_SEARCH)
            log("consumers: capability VERBATIM_SEARCH=$verbatim (recorded; general query attempted regardless)")

            stage = "query"
            val spec = SearchSpec.Builder()
                .addFilterSchemas(GSAI_SCHEMA)
                .setResultCountPerPage(CONSUMER_PAGE_SIZE)
                .build()
            val results = session.search("", spec)
            try {
                while (true) {
                    stage = "page"
                    val page = results.getNextPageAsync().await()
                    if (page.isEmpty()) break
                    if (pages >= CONSUMER_MAX_PAGES) {
                        truncated = true
                        break
                    }
                    pages++
                    for (result in page) {
                        entries++
                        val doc = result.genericDocument
                        val typeLabel = if (doc.getProperty("applicationType") == null) {
                            "MISSING"
                        } else {
                            when (doc.getPropertyLong("applicationType")) {
                                0L -> "PRODUCER"
                                1L -> "CONSUMER"
                                else -> "UNKNOWN"
                            }
                        }
                        val schemaTypes = doc.getPropertyStringArray("schemaTypes")?.toList() ?: emptyList()
                        log("consumers: pkg=${result.packageName} db=${result.databaseName} " +
                            "applicationType=$typeLabel schemaTypes=$schemaTypes")
                    }
                }
            } finally {
                results.close()
            }

            val outcome = when {
                truncated -> ConsumerSurvey.TRUNCATED
                entries > 0 -> ConsumerSurvey.FOUND
                else -> ConsumerSurvey.NONE_VISIBLE
            }
            log("consumers: result=$outcome pages=$pages entries=$entries verbatimSearch=$verbatim" +
                if (outcome == ConsumerSurvey.TRUNCATED) " maxPages=$CONSUMER_MAX_PAGES" else "")
            if (outcome == ConsumerSurvey.NONE_VISIBLE) {
                log("consumers: none visible to this package. This does NOT prove the reader is unsupported.")
            }
        } catch (t: Throwable) {
            log("consumers: result=${ConsumerSurvey.ERROR} stage=$stage error=${describe(t)} " +
                "pagesRead=$pages entriesLogged=$entries")
        } finally {
            session.close()
        }
    }

    private fun describe(t: Throwable): String =
        if (t is AppSearchException) "AppSearchException(resultCode=${t.resultCode})"
        else t.javaClass.simpleName

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
