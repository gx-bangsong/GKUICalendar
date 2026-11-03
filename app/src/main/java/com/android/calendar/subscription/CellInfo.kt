/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription

import androidx.annotation.ColorInt

/**
 * Render instruction for one subscription chip/badge in a month-view cell.
 *
 * @param badgeColor  When non-null, an ARGB color to apply to the badge text
 *                    (takes precedence over the default theme paint). Used for
 *                    color-coded shift types.
 */
data class CellInfo(
    val providerId: String,
    val primaryText: String?,
    val secondaryText: String? = null,
    @ColorInt val badgeColor: Int? = null,
    val backgroundStyle: BgStyle = BgStyle.NONE,
    val contentDescription: String? = null
)
