/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.traffic

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.BgStyle
import com.android.calendar.subscription.CellInfo
import com.android.calendar.subscription.SubscriptionProvider
import com.android.calendar.subscription.traffic.data.TrafficRules
import com.android.calendar.subscription.traffic.ui.TrafficSettingsFragment
import ws.xsoh.etar.R
import java.util.TimeZone

/**
 * License-plate driving-restriction ("限行") subscription, modelled on the
 * MIUI calendar's *traffic restriction info* row: every weekday cell shows
 * the restricted tail digits, and the user's own plate lights up in red on
 * the days they may not drive.
 *
 * Settings are stored in SharedPreferences: rule mode (tail-number rotation
 * vs 单双号), the user's plate tail digit, and the rotation group offset
 * (published quarterly by the traffic bureau).
 */
object TrafficProvider : SubscriptionProvider {

    override val id: String = "traffic"
    override val displayNameRes: Int = R.string.sub_traffic_name
    override val summaryRes: Int = R.string.sub_traffic_summary
    override val iconRes: Int = R.drawable.ic_sub_traffic
    override val priority: Int = 30

    private const val PREFS = "subscription_traffic"
    private const val KEY_ENABLED = "traffic_enabled"
    private const val KEY_MODE = "traffic_mode"
    private const val KEY_TAIL = "traffic_tail_digit"
    private const val KEY_GROUP = "traffic_group_offset"
    private const val NO_TAIL = -1

    /** Red used for "your plate is restricted today". */
    private const val COLOR_RESTRICTED = 0xFFD32F2F.toInt() // red-700
    /** Muted color for the informational digits of other days. */
    private const val COLOR_INFO = 0xFF9E9E9E.toInt()       // grey-500

    override fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    override fun getCellInfo(ctx: Context, julianDay: Int): CellInfo? {
        if (TrafficRules.isWeekend(julianDay)) return null
        val mode = getMode(ctx)
        val tail = getTailDigit(ctx)
        if (mode == TrafficRules.MODE_ODD_EVEN) {
            if (tail == NO_TAIL) return null
            val dom = dayOfMonth(julianDay)
            if (!TrafficRules.isRestricted(mode, tail, 0, julianDay, dom)) return null
            return CellInfo(
                providerId = id,
                primaryText = ctx.getString(R.string.sub_traffic_badge_restricted),
                badgeColor = COLOR_RESTRICTED,
                backgroundStyle = BgStyle.NONE,
                contentDescription = ctx.getString(R.string.sub_traffic_badge_restricted)
            )
        }
        val digits = TrafficRules.restrictedDigits(julianDay, getGroupOffset(ctx, julianDay))
            ?: return null
        val text = ctx.getString(R.string.sub_traffic_badge_digits_fmt, digits[0], digits[1])
        val mine = tail != NO_TAIL && (digits[0] == tail || digits[1] == tail)
        return CellInfo(
            providerId = id,
            primaryText = text,
            badgeColor = if (mine) COLOR_RESTRICTED else COLOR_INFO,
            backgroundStyle = BgStyle.NONE,
            contentDescription = text
        )
    }

    override fun getSettingsFragmentClass(): Class<out Fragment> =
        TrafficSettingsFragment::class.java

    override fun onEnabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, true).apply()
    }

    override fun onDisabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    override fun getCurrentSummary(ctx: Context): String? {
        if (!isEnabled(ctx)) return null
        val tail = getTailDigit(ctx)
        if (getMode(ctx) == TrafficRules.MODE_ODD_EVEN) {
            return ctx.getString(R.string.sub_traffic_mode_odd_even)
        }
        return if (tail == NO_TAIL) {
            ctx.getString(R.string.sub_traffic_mode_tail)
        } else {
            ctx.getString(R.string.sub_traffic_summary_tail_fmt, tail)
        }
    }

    // ---- settings accessors -------------------------------------------------

    @JvmStatic
    fun getMode(ctx: Context): Int = prefs(ctx).getInt(KEY_MODE, TrafficRules.MODE_TAIL_NUMBER)

    @JvmStatic
    fun setMode(ctx: Context, mode: Int) {
        prefs(ctx).edit().putInt(KEY_MODE, mode).apply()
    }

    /** @return 0..9, or -1 when the user hasn't entered a plate yet. */
    @JvmStatic
    fun getTailDigit(ctx: Context): Int = prefs(ctx).getInt(KEY_TAIL, NO_TAIL)

    @JvmStatic
    fun setTailDigit(ctx: Context, digit: Int) {
        prefs(ctx).edit().putInt(KEY_TAIL, digit).apply()
    }

    @JvmStatic
    fun getGroupOffset(ctx: Context, julianDay: Int): Int {
        val stored = prefs(ctx).getInt(KEY_GROUP, Int.MIN_VALUE)
        return if (stored == Int.MIN_VALUE) TrafficRules.defaultGroupOffset(julianDay) else stored
    }

    @JvmStatic
    fun setGroupOffset(ctx: Context, offset: Int) {
        prefs(ctx).edit().putInt(KEY_GROUP, offset).apply()
    }

    @JvmStatic
    fun todayJulianDay(): Int {
        val now = System.currentTimeMillis()
        return Time.getJulianDay(now, TimeZone.getDefault().getOffset(now) / 1000L)
    }

    private fun dayOfMonth(julianDay: Int): Int {
        val t = Time(TimeZone.getDefault().id)
        t.setJulianDay(julianDay)
        t.normalize()
        return t.getDay()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
