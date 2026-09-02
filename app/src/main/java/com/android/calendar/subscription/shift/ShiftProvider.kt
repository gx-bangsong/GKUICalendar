/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.shift

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.BgStyle
import com.android.calendar.subscription.CellInfo
import com.android.calendar.subscription.SubscriptionProvider
import com.android.calendar.subscription.shift.data.ShiftEngine
import com.android.calendar.subscription.shift.data.ShiftPresets
import com.android.calendar.subscription.shift.data.ShiftType
import com.android.calendar.subscription.shift.ui.ShiftSettingsFragment
import ws.xsoh.etar.R
import java.util.TimeZone

/**
 * Xiaomi-MIUI/HyperOS-style 轮班助手 (shift assistant) provider.
 *
 * The model is a custom cycle of N days (1..14), each day tagged with one
 * of 早/中/晚/休, anchored at a user-chosen Julian day. Quick-preset
 * buttons fill in one of the Xiaomi-standard templates (三班倒, 四班三倒,
 * 上一休一, 上二休二, 常白班); the per-day editor grid lets the user tweak
 * any slot afterwards — exactly matching MIUI's 倒班提醒设置 screen.
 *
 * Persistence is SharedPreferences-only (no Room/WorkManager in Phase 1).
 */
object ShiftProvider : SubscriptionProvider {

    override val id: String = "shift"
    override val displayNameRes: Int = R.string.sub_shift_name
    override val summaryRes: Int = R.string.sub_shift_summary
    override val iconRes: Int = R.drawable.ic_sub_shift
    override val priority: Int = 20

    private const val PREFS = "subscription_shift"
    private const val KEY_ENABLED = "shift_enabled_v2"
    private const val KEY_CYCLE = "shift_cycle_v2"      // comma-separated ShiftType codes
    private const val KEY_ANCHOR = "shift_anchor_jd_v2"
    private const val SEP = ","

    override fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    override fun getCellInfo(ctx: Context, julianDay: Int): CellInfo? {
        val (cycle, anchor) = loadState(ctx) ?: return null
        val type = ShiftEngine.typeFor(cycle, anchor, julianDay)
        if (type < 0 || type == ShiftType.REST) return null
        return CellInfo(
            providerId = id,
            primaryText = ShiftEngine.labelFor(type),
            badgeColor = ShiftPresets.badgeColor(type),
            backgroundStyle = BgStyle.NONE
        )
    }

    override fun getSettingsFragmentClass(): Class<out Fragment> =
        ShiftSettingsFragment::class.java

    override fun onEnabled(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains(KEY_CYCLE)) {
            saveState(ctx, ShiftPresets.cycleForKey(ShiftPresets.KEY_THREE)!!, todayJulianDay())
            p.edit().putBoolean(KEY_ENABLED, true).apply()
        } else {
            p.edit().putBoolean(KEY_ENABLED, true).apply()
        }
    }

    override fun onDisabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    override fun getCurrentSummary(ctx: Context): String? {
        val (cycle, _) = loadState(ctx) ?: return null
        return summarize(cycle)
    }

    /** Applies a quick preset anchored at today. */
    @JvmStatic
    fun applyPresetToday(ctx: Context, key: String) {
        val cycle = ShiftPresets.cycleForKey(key) ?: return
        saveState(ctx, cycle, todayJulianDay())
        prefs(ctx).edit().putBoolean(KEY_ENABLED, true).apply()
    }

    /** Saves a custom cycle + anchor and enables the subscription. */
    @JvmStatic
    fun saveCustomCycle(ctx: Context, cycle: IntArray, anchorJulianDay: Int) {
        saveState(ctx, cycle, anchorJulianDay)
        prefs(ctx).edit().putBoolean(KEY_ENABLED, true).apply()
    }

    /** @return (cycle, anchorJd), or null if disabled/unset. */
    @JvmStatic
    fun loadState(ctx: Context): Pair<IntArray, Int>? {
        val p = prefs(ctx)
        if (!p.getBoolean(KEY_ENABLED, false)) return null
        val raw = p.getString(KEY_CYCLE, null) ?: return null
        val cycle = parseCycle(raw) ?: return null
        val anchor = p.getInt(KEY_ANCHOR, Int.MIN_VALUE)
        if (anchor == Int.MIN_VALUE) return null
        return cycle to anchor
    }

    private fun saveState(ctx: Context, cycle: IntArray, anchor: Int) {
        prefs(ctx).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_CYCLE, cycle.joinToString(SEP))
            .putInt(KEY_ANCHOR, anchor)
            .apply()
    }

    private fun parseCycle(raw: String): IntArray? {
        val parts = raw.split(SEP).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val out = IntArray(parts.size)
        for (i in parts.indices) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in ShiftType.MORNING..ShiftType.REST) return null
            out[i] = v
        }
        return out
    }

    private fun summarize(cycle: IntArray): String {
        // Find a matching preset name; otherwise show "N天一循环"
        for (key in ShiftPresets.ORDERED_KEYS) {
            val template = ShiftPresets.cycleForKey(key) ?: continue
            if (template.contentEquals(cycle)) return ShiftPresets.displayName(key)
        }
        return "${cycle.size}天一循环"
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayJulianDay(): Int {
        val tz = TimeZone.getDefault()
        return Time.getJulianDay(System.currentTimeMillis(),
            tz.getOffset(System.currentTimeMillis()) / 1000L)
    }
}
