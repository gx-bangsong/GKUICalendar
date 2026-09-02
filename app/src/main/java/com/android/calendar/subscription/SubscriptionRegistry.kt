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
