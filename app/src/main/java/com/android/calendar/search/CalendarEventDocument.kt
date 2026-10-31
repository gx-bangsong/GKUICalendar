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

import androidx.appsearch.app.GenericDocument

/**
 * One calendar event as indexed into AppSearch. Plain data; the AppSearch document
 * is built by [toGenericDocument]. The document id is stable per provider event id,
 * so re-writing the same event overwrites instead of duplicating.
 */
data class CalendarEventDocument(
    val eventId: Long,
    val title: String,
    val description: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
) {
    /** Stable document id: same event always maps to the same document. */
    fun documentId(): String = "event:$eventId"

    fun toGenericDocument(): GenericDocument {
        val builder = GenericDocument.Builder<GenericDocument.Builder<*>>(
            CalendarAppSearchIndexer.EVENTS_NAMESPACE,
            documentId(),
            CalendarAppSearchIndexer.EVENTS_SCHEMA_TYPE
        )
        builder.setPropertyLong(CalendarAppSearchIndexer.PROP_EVENT_ID, eventId)
        builder.setPropertyString(CalendarAppSearchIndexer.PROP_TITLE, title)
        builder.setPropertyString(CalendarAppSearchIndexer.PROP_DESCRIPTION, description)
        builder.setPropertyString(CalendarAppSearchIndexer.PROP_LOCATION, location)
        builder.setPropertyLong(CalendarAppSearchIndexer.PROP_START_MILLIS, startMillis)
        builder.setPropertyLong(CalendarAppSearchIndexer.PROP_END_MILLIS, endMillis)
        builder.setPropertyBoolean(CalendarAppSearchIndexer.PROP_ALL_DAY, allDay)
        return builder.build()
    }
}
