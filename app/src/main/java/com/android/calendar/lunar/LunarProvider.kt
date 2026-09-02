/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.lunar

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.calendar.Utils
import com.android.calendar.settings.LunarPreferences
import com.android.calendar.subscription.CellInfo
import com.android.calendar.subscription.SubscriptionProvider
import ws.xsoh.etar.R

/**
 * [SubscriptionProvider] adapter for the existing contextual lunar calendar.
 *
 * For Phase 1a, [getCellInfo] always returns null: Lunar continues to draw
 * through the existing [LunarDayRenderer]. This adapter exists so the
 * Subscription Hub can list a Lunar row with toggle + summary. Phase 1c/2
 * will migrate rendering to CellInfo once the generic chip renderer is ready.
 */
object LunarProvider : SubscriptionProvider {
    override val id: String = "lunar"
    override val displayNameRes: Int = R.string.sub_lunar_name
    override val summaryRes: Int = R.string.sub_lunar_summary
    override val iconRes: Int = R.drawable.ic_sub_lunar
    override val priority: Int = 10

    override fun isEnabled(ctx: Context): Boolean = Utils.getLunarMode(ctx) != LunarMode.OFF
    override fun getCellInfo(ctx: Context, julianDay: Int): CellInfo? = null
    override fun getSettingsFragmentClass(): Class<out Fragment> =
        LunarPreferences::class.java
}
