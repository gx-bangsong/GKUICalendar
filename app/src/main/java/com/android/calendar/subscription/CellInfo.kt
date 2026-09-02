/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription

import androidx.annotation.AttrRes

/**
 * Render instruction for one subscription chip/badge in a month-view cell.
 */
data class CellInfo(
    val providerId: String,
    val primaryText: String?,
    val secondaryText: String? = null,
    @get:AttrRes val badgeColorAttr: Int? = null,
    val backgroundStyle: BgStyle = BgStyle.NONE,
    val contentDescription: String? = null
)
