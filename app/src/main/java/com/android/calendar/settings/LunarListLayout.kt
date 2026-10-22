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
import android.view.View.MeasureSpec
import android.widget.FrameLayout

/**
 * Holds the settings list and limits its width to [maxWidthPx].
 *
 * The limit is applied in [onMeasure], before any child is measured. Every child therefore uses
 * the same width from its first layout pass, and nothing is changed after layout. The parent
 * centres this view with layout_gravity, so the list does not need any padding changes.
 *
 * A window narrower than the limit gets its full width, as before. The limit is re-applied on
 * every measure, so rotation, split-screen and window resizing are handled by the normal layout
 * passes.
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
