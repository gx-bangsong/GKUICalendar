/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.birthday

import android.content.Context
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.BgStyle
import com.android.calendar.subscription.CellInfo
import com.android.calendar.subscription.SubscriptionProvider
import com.android.calendar.subscription.birthday.data.LunarBirthday
import com.android.calendar.subscription.birthday.ui.BirthdaySettingsFragment
import com.nlf.calendar.Solar
import ws.xsoh.etar.R
import java.util.TimeZone

/**
 * 农历生日 (lunar birthdays) subscription.
 *
 * Each entry stores a lunar month/day; on every rendered day the provider
 * converts the solar date to its lunar counterpart and matches. That keeps
 * the model tiny (no per-year expansion) and automatically correct across
 * years, including leap months — a leap month birthday falls back to the
 * ordinary month, matching common Chinese calendar practice.
 */
object BirthdayProvider : SubscriptionProvider {

    override val id: String = "lunar_birthday"
    override val displayNameRes: Int = R.string.sub_lunar_birthday_name
    override val summaryRes: Int = R.string.sub_lunar_birthday_summary
    override val iconRes: Int = R.drawable.ic_sub_cake
    override val priority: Int = 50

    private const val PREFS = "subscription_birthday"
    private const val KEY_ENABLED = "birthday_enabled"
    private const val KEY_ITEMS = "birthday_items"

    private const val COLOR_BIRTHDAY = 0xFFC2185B.toInt() // pink-700

    override fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    override fun getCellInfo(ctx: Context, julianDay: Int): CellInfo? {
        val items = getBirthdays(ctx)
        if (items.isEmpty()) return null
        val t = Time(TimeZone.getDefault().id)
        t.setJulianDay(julianDay)
        t.normalize()
        val lunar = Solar.fromYmd(t.getYear(), t.getMonth() + 1, t.getDay()).getLunar()
        val lunarMonth = Math.abs(lunar.getMonth())
        val lunarDay = lunar.getDay()
        for (b in items) {
            if (b.lunarMonth == lunarMonth && b.lunarDay == lunarDay) {
                val text = ctx.getString(R.string.sub_birthday_badge_fmt, b.name)
                return CellInfo(
                    providerId = id,
                    primaryText = text,
                    badgeColor = COLOR_BIRTHDAY,
                    backgroundStyle = BgStyle.NONE,
                    contentDescription = text
                )
            }
        }
        return null
    }

    override fun getSettingsFragmentClass(): Class<out Fragment> =
        BirthdaySettingsFragment::class.java

    override fun onEnabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, true).apply()
    }

    override fun onDisabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    override fun getCurrentSummary(ctx: Context): String? {
        if (!isEnabled(ctx)) return null
        val n = getBirthdays(ctx).size
        return if (n == 0) ctx.getString(R.string.sub_birthday_empty)
        else ctx.getString(R.string.sub_birthday_count_fmt, n)
    }

    @JvmStatic
    fun getBirthdays(ctx: Context): List<LunarBirthday> =
        LunarBirthday.parseAll(prefs(ctx).getString(KEY_ITEMS, null))

    @JvmStatic
    fun setBirthdays(ctx: Context, items: List<LunarBirthday>) {
        prefs(ctx).edit()
            .putString(KEY_ITEMS, LunarBirthday.serializeAll(items))
            .apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
