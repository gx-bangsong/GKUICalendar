/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.traffic

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.calendar.subscription.CellInfo
import com.android.calendar.subscription.SubscriptionProvider
import com.android.calendar.subscription.traffic.ui.TrafficSettingsFragment
import ws.xsoh.etar.R

/**
 * Phase 1c placeholder for license-plate driving-restriction reminders
 * ("限行"). [getCellInfo] returns null until Phase 2 implements city
 * rules, and the settings screen shows a Coming-soon panel.
 */
object TrafficProvider : SubscriptionProvider {

    override val id: String = "traffic"
    override val displayNameRes: Int = R.string.sub_traffic_name
    override val summaryRes: Int = R.string.sub_traffic_summary
    override val iconRes: Int = R.drawable.ic_sub_traffic
    override val priority: Int = 30

    private const val PREFS = "subscription_traffic"
    private const val KEY_ENABLED = "traffic_enabled"

    override fun isEnabled(ctx: Context): Boolean =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    override fun getCellInfo(ctx: Context, julianDay: Int): CellInfo? = null

    override fun getSettingsFragmentClass(): Class<out Fragment> =
        TrafficSettingsFragment::class.java

    override fun onEnabled(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, true).apply()
    }

    override fun onDisabled(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()
    }
}
