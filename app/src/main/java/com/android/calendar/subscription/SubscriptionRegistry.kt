/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription

import android.content.Context
import com.android.calendar.lunar.LunarProvider
import com.android.calendar.subscription.birthday.BirthdayProvider
import com.android.calendar.subscription.period.PeriodProvider
import com.android.calendar.subscription.shift.ShiftProvider
import com.android.calendar.subscription.traffic.TrafficProvider

object SubscriptionRegistry {

    @JvmStatic
    fun getAll(): List<SubscriptionProvider> = listOf(LunarProvider, ShiftProvider, TrafficProvider, PeriodProvider, BirthdayProvider)

    /**
     * Day-list items of every enabled provider, in provider order. A provider that fails
     * is skipped so one broken subscription does not hide the others.
     */
    @JvmStatic
    fun getEnabledDayItems(ctx: Context, julianDay: Int): List<Pair<SubscriptionProvider, CellInfo>> {
        val out = ArrayList<Pair<SubscriptionProvider, CellInfo>>()
        for (p in getAll()) {
            val enabled = try {
                p.isEnabled(ctx)
            } catch (e: Exception) {
                false
            }
            if (!enabled) continue
            val items = try {
                p.getDayItems(ctx, julianDay)
            } catch (e: Exception) {
                emptyList()
            }
            for (info in items) out.add(p to info)
        }
        return out
    }

    @JvmStatic
    fun getEnabledCellInfos(ctx: Context, julianDay: Int): List<CellInfo> {
        val all = getAll()
        val result = ArrayList<CellInfo>(all.size)
        for (p in all) {
            if (!p.isEnabled(ctx)) continue
            val info = p.getCellInfo(ctx, julianDay) ?: continue
            result.add(info)
        }
        result.reverse()
        return result
    }
}
