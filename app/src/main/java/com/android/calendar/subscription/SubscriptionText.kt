/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * Renders the enabled subscriptions for a day as text, for the views that
 * lay out with Views/Canvas text rather than the month grid's custom chip
 * painter (agenda rows, and the day/week header).
 *
 * Month view keeps its own painter because it positions each badge inside a
 * cell; everything else just needs "早 · 3和8" style inline text, optionally
 * colored per provider.
 */
object SubscriptionText {

    /** Separator between two providers' badges on the same day. */
    private const val SEP = "  "

    /**
     * @return plain concatenated badge text for [julianDay], or null when no
     *         enabled provider has anything to show.
     */
    @JvmStatic
    fun plain(ctx: Context, julianDay: Int): String? {
        val infos = SubscriptionRegistry.getEnabledCellInfos(ctx, julianDay)
        if (infos.isEmpty()) return null
        val sb = StringBuilder()
        for (ci in infos) {
            val t = ci.primaryText ?: continue
            if (sb.isNotEmpty()) sb.append(SEP)
            sb.append(t)
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /**
     * Same as [plain] but each provider's badge keeps its own color, for
     * TextViews (agenda day headers).
     *
     * @return null when nothing is to be shown.
     */
    @JvmStatic
    fun colored(ctx: Context, julianDay: Int, fallbackColor: Int): CharSequence? {
        val infos = SubscriptionRegistry.getEnabledCellInfos(ctx, julianDay)
        if (infos.isEmpty()) return null
        val sb = SpannableStringBuilder()
        for (ci in infos) {
            val t = ci.primaryText ?: continue
            if (sb.isNotEmpty()) sb.append(SEP)
            val start = sb.length
            sb.append(t)
            val color = ci.badgeColor ?: fallbackColor
            sb.setSpan(ForegroundColorSpan(color), start, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return if (sb.isEmpty()) null else sb
    }

    /** True when at least one enabled provider renders something that day. */
    @JvmStatic
    fun hasAny(ctx: Context, julianDay: Int): Boolean =
        SubscriptionRegistry.getEnabledCellInfos(ctx, julianDay).isNotEmpty()
}
