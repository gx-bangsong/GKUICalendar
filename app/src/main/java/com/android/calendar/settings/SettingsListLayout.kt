/*
 * Copyright (C) 2026 The Etar Calendar Authors
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
package com.android.calendar.settings

import android.content.Context
import android.view.Gravity
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * Maximum width of a settings list on a wide window. This is a project choice, not an MD3
 * requirement; the MD3 medium window width is 600dp.
 */
internal const val SETTINGS_LIST_MAX_WIDTH_DP = 600

/**
 * Wraps a settings list so that it is at most [SETTINGS_LIST_MAX_WIDTH_DP] wide and centred in
 * its parent. Call it from `onCreateRecyclerView`, and return the list it was given.
 *
 * The limit is applied in the measure pass, before any child is measured. Every row and heading
 * therefore uses the same width from its first layout, and nothing is changed after layout. This
 * replaces the earlier approach that rewrote the list padding in an OnLayoutChangeListener.
 *
 * Only the list's own container is changed. The framework's RecyclerView keeps its ID, layout
 * manager and accessibility delegate, so the rest of the fragment is unchanged. If the parent is
 * not a FrameLayout, or the framework has already placed the list (Android Auto), the list is
 * returned unchanged.
 */
internal fun wrapSettingsListWidth(
    list: RecyclerView,
    parent: ViewGroup,
    density: Float,
): RecyclerView {
    if (list.parent != null || parent !is FrameLayout) return list
    val maxWidthPx = (SETTINGS_LIST_MAX_WIDTH_DP * density).roundToInt()
    val column = MaxWidthFrameLayout(parent.context, maxWidthPx)
    column.addView(
        list,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
    parent.addView(
        column,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_HORIZONTAL
        )
    )
    return list
}

/**
 * Holds a list and limits its width to [maxWidthPx] in the measure pass. A window narrower than
 * the limit keeps its full width. The limit is applied on every measure, so rotation,
 * split-screen and window resizing are handled by the normal layout passes.
 */
internal class MaxWidthFrameLayout(
    context: Context,
    private val maxWidthPx: Int,
) : FrameLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val constrainedWidthSpec =
            if (mode != MeasureSpec.UNSPECIFIED && size > maxWidthPx) {
                // Keep the mode the parent asked for; only the size is capped.
                MeasureSpec.makeMeasureSpec(maxWidthPx, mode)
            } else {
                widthMeasureSpec
            }
        super.onMeasure(constrainedWidthSpec, heightMeasureSpec)
    }
}
