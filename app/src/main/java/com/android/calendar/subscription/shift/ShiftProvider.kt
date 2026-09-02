/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.shift

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.CellInfo
import com.android.calendar.subscription.BgStyle
import com.android.calendar.subscription.SubscriptionProvider
import com.android.calendar.subscription.shift.data.ShiftEngine
import com.android.calendar.subscription.shift.data.ShiftPresets
import com.android.calendar.subscription.shift.ui.ShiftSettingsFragment
import ws.xsoh.etar.R
import java.util.TimeZone

/**
 * Xiaomi-inspired shift assistant [SubscriptionProvider].
 *
 * Phase 1b uses a minimal SharedPreferences model: one preset key
 * (三班倒 / 四班三倒 / 上二休二) and an anchor Julian day (defaulting
 * to "today" on enable). Phase 2 adds custom cycles, per-day overrides,
 * alarms, and CalendarContract sync.
 *
 * The rendered badge is a small single-char label (早/中/晚/休) drawn
 * in the lower-left of the day cell.
 */
object ShiftProvider : SubscriptionProvider {

    override val id: String = "shift"
    override val displayNameRes: Int = R.string.sub_shift_name
    override val summaryRes: Int = R.string.sub_shift_summary
    override val iconRes: Int = R.drawable.ic_sub_shift
    override val priority: Int = 20

    private const val PREFS = "subscription_shift"
    private const val KEY_ENABLED = "shift_enabled_v2"
    private const val KEY_PRESET = "shift_preset_v2"
    private const val KEY_ANCHOR = "shift_anchor_jd_v2"

    override fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    override fun getCellInfo(ctx: Context, julianDay: Int): CellInfo? {
        val p = prefs(ctx)
        if (!p.getBoolean(KEY_ENABLED, false)) return null
        val key = p.getString(KEY_PRESET, null) ?: return null
        val cycle = ShiftPresets.cycleForKey(key) ?: return null
        val anchor = p.getInt(KEY_ANCHOR, Int.MIN_VALUE)
        if (anchor == Int.MIN_VALUE) return null
        val type = ShiftEngine.typeFor(cycle, anchor, julianDay)
        if (type < 0) return null
        return CellInfo(
            providerId = id,
            primaryText = ShiftEngine.labelFor(type),
            backgroundStyle = BgStyle.NONE
        )
    }

    override fun getSettingsFragmentClass(): Class<out Fragment> =
        ShiftSettingsFragment::class.java

    override fun onEnabled(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains(KEY_PRESET)) {
            p.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_PRESET, ShiftPresets.KEY_THREE)
                .putInt(KEY_ANCHOR, todayJulianDay())
                .apply()
        } else {
            p.edit().putBoolean(KEY_ENABLED, true).apply()
        }
    }

    override fun onDisabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    override fun getCurrentSummary(ctx: Context): String? {
        val p = prefs(ctx)
        if (!p.getBoolean(KEY_ENABLED, false)) return null
        val key = p.getString(KEY_PRESET, null) ?: return null
        return ShiftPresets.displayName(key)
    }

    @JvmStatic
    fun applyPresetToday(ctx: Context, key: String) {
        if (ShiftPresets.cycleForKey(key) == null) return
        prefs(ctx).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_PRESET, key)
            .putInt(KEY_ANCHOR, todayJulianDay())
            .apply()
    }

    @JvmStatic
    fun currentState(ctx: Context): Pair<IntArray, Int>? {
        val p = prefs(ctx)
        if (!p.getBoolean(KEY_ENABLED, false)) return null
        val key = p.getString(KEY_PRESET, null) ?: return null
        val cycle = ShiftPresets.cycleForKey(key) ?: return null
        val anchor = p.getInt(KEY_ANCHOR, Int.MIN_VALUE)
        if (anchor == Int.MIN_VALUE) return null
        return cycle to anchor
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayJulianDay(): Int {
        val tz = TimeZone.getDefault()
        return Time.getJulianDay(System.currentTimeMillis(),
            tz.getOffset(System.currentTimeMillis()) / 1000L)
    }
}
