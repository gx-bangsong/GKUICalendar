/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.android.calendar.Event

/** One row of the selected day's schedule: a subscription card or a calendar event. */
sealed class AgendaItem {

    /**
     * Daily content from an enabled subscription (birthday, shift, rest day, period, traffic).
     * Cards are read-only; they never open the subscription management page.
     */
    data class Card(
        val text: String,
        val source: String,
        @ColorInt val color: Int?,
        @DrawableRes val iconRes: Int
    ) : AgendaItem()

    class Entry(val event: Event) : AgendaItem()
}
