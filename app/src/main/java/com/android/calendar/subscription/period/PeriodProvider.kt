/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.period

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.BgStyle
import com.android.calendar.subscription.CellInfo
import com.android.calendar.subscription.SubscriptionProvider
import com.android.calendar.subscription.period.data.PeriodEngine
import com.android.calendar.subscription.period.ui.PeriodSettingsFragment
import ws.xsoh.etar.R
import java.util.TimeZone

/**
 * 生理期 (period tracker) subscription: marks bleeding days, the predicted
 * next start, the fertile window and the estimated ovulation day in the
 * month view. All data stays on-device in SharedPreferences.
 */
object PeriodProvider : SubscriptionProvider {

    override val id: String = "period"
    override val displayNameRes: Int = R.string.sub_period_name
    override val summaryRes: Int = R.string.sub_period_summary
    override val iconRes: Int = R.drawable.ic_sub_period
    override val priority: Int = 40

    private const val PREFS = "subscription_period"
    private const val KEY_ENABLED = "period_enabled"
    private const val KEY_ANCHOR = "period_anchor_jd"
    private const val KEY_CYCLE = "period_cycle_length"
    private const val KEY_LENGTH = "period_period_length"

    private const val COLOR_PERIOD = 0xFFE91E63.toInt()    // pink-500
    private const val COLOR_PREDICTED = 0xFFF48FB1.toInt() // pink-200
    private const val COLOR_FERTILE = 0xFF7E57C2.toInt()   // deep-purple-400
    private const val COLOR_OVULATION = 0xFF5E35B1.toInt() // deep-purple-600

    override fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    override fun getCellInfo(ctx: Context, julianDay: Int): CellInfo? {
        val anchor = getAnchor(ctx)
        if (anchor == NO_ANCHOR) return null
        val phase = PeriodEngine.phaseFor(
            anchor, getCycleLength(ctx), getPeriodLength(ctx), julianDay)
        val textRes: Int
        val color: Int
        when (phase) {
            PeriodEngine.PERIOD -> { textRes = R.string.sub_period_badge_period; color = COLOR_PERIOD }
            PeriodEngine.PREDICTED_START -> {
                textRes = R.string.sub_period_badge_predicted; color = COLOR_PREDICTED
            }
            PeriodEngine.OVULATION -> {
                textRes = R.string.sub_period_badge_ovulation; color = COLOR_OVULATION
            }
            PeriodEngine.FERTILE -> { textRes = R.string.sub_period_badge_fertile; color = COLOR_FERTILE }
            else -> return null
        }
        val text = ctx.getString(textRes)
        return CellInfo(
            providerId = id,
            primaryText = text,
            badgeColor = color,
            backgroundStyle = BgStyle.NONE,
            contentDescription = text
        )
    }

    override fun getSettingsFragmentClass(): Class<out Fragment> =
        PeriodSettingsFragment::class.java

    override fun onEnabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, true).apply()
    }

    override fun onDisabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    override fun getCurrentSummary(ctx: Context): String? {
        if (!isEnabled(ctx)) return null
        val anchor = getAnchor(ctx)
        if (anchor == NO_ANCHOR) return ctx.getString(R.string.sub_period_no_anchor)
        val days = PeriodEngine.daysUntilNextStart(anchor, getCycleLength(ctx), todayJulianDay())
        return if (days == 0) ctx.getString(R.string.sub_period_starts_today)
        else ctx.getString(R.string.sub_period_days_until_fmt, days)
    }

    // ---- settings accessors -------------------------------------------------

    const val NO_ANCHOR = Int.MIN_VALUE

    @JvmStatic
    fun getAnchor(ctx: Context): Int = prefs(ctx).getInt(KEY_ANCHOR, NO_ANCHOR)

    @JvmStatic
    fun setAnchor(ctx: Context, julianDay: Int) {
        prefs(ctx).edit().putInt(KEY_ANCHOR, julianDay).apply()
    }

    @JvmStatic
    fun getCycleLength(ctx: Context): Int =
        prefs(ctx).getInt(KEY_CYCLE, PeriodEngine.DEFAULT_CYCLE_LENGTH)

    @JvmStatic
    fun setCycleLength(ctx: Context, days: Int) {
        prefs(ctx).edit().putInt(KEY_CYCLE, PeriodEngine.clampCycleLength(days)).apply()
    }

    @JvmStatic
    fun getPeriodLength(ctx: Context): Int =
        prefs(ctx).getInt(KEY_LENGTH, PeriodEngine.DEFAULT_PERIOD_LENGTH)

    @JvmStatic
    fun setPeriodLength(ctx: Context, days: Int) {
        prefs(ctx).edit().putInt(KEY_LENGTH, PeriodEngine.clampPeriodLength(days)).apply()
    }

    @JvmStatic
    fun todayJulianDay(): Int {
        val now = System.currentTimeMillis()
        return Time.getJulianDay(now, TimeZone.getDefault().getOffset(now) / 1000L)
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
