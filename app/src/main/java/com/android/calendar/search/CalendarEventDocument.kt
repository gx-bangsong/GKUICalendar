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

/**
 * Plain-data holder for one calendar event as it will be indexed
 * into AppSearch.
 *
 * This is deliberately not annotated with [androidx.appsearch.annotation.Document].
 * The 1.0.0-alpha04 @Document processor would force the project's
 * Kotlin compilation to round-trip through kapt stub generation,
 * which interacts badly with the existing Room kapt pipeline and
 * trips the gradle-generateBp step's working-tree check. Writing
 * the schema manually with AppSearchSchema.Builder (in the
 * indexer) keeps the same on-device shape while sidestepping both
 * concerns.
 *
 * Property mapping:
 *   - id, namespace          -> StringPropertyConfig (INDEXING_TYPE_EXACT_TERMS,
 *                                TOKENIZER_TYPE_PLAIN, required for the
 *                                namespace and id columns AppSearch
 *                                writes behind the scenes).
 *   - title, description,    -> StringPropertyConfig (default
 *     location                 indexing / tokenizer, indexed for
 *                                free-text search by Pixel Launcher
 *                                / system search).
 *   - startMillis,           -> LongPropertyConfig.
 *     endMillis
 *   - allDay                 -> BooleanPropertyConfig.
 */
data class CalendarEventDocument(
    val id: String,
    val namespace: String,
    val title: String,
    val description: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)
