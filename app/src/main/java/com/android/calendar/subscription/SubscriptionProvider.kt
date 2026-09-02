/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * Pluggable data source for month-view contextual information.
 */
interface SubscriptionProvider {
    val id: String
    @get:StringRes val displayNameRes: Int
    @get:StringRes val summaryRes: Int
    @get:DrawableRes val iconRes: Int
    /** Higher priority → drawn on top. */
    val priority: Int

    fun isEnabled(ctx: Context): Boolean
    fun getCellInfo(ctx: Context, julianDay: Int): CellInfo?
    fun getSettingsFragmentClass(): Class<out Fragment>
    fun onEnabled(ctx: Context) {}
    fun onDisabled(ctx: Context) {}
    fun getCurrentSummary(ctx: Context): String? = null
}
