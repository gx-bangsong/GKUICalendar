/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

/**
 * Pure rules for the month-and-day home screen. This file must not reference
 * Android types so the rules can run in JVM unit tests.
 */
object HomeLaunchRules {
    const val HOME_LAYOUT_CLASSIC = "classic"
    const val HOME_LAYOUT_NEW = "new"

    private const val ACTION_MAIN = "android.intent.action.MAIN"

    /**
     * Stored value of the home-layout preference. A missing value (never written)
     * means classic, so an unset preference keeps the existing behaviour.
     */
    @JvmStatic
    fun isNewHomeSelected(storedValue: String?): Boolean = storedValue == HOME_LAYOUT_NEW

    /**
     * A plain launch has no action other than MAIN, no data URI and no extras.
     * Notifications, widgets and external event Intents carry a target, so they
     * never match this rule and keep their original destination.
     */
    @JvmStatic
    fun isPlainLaunch(action: String?, hasData: Boolean, extrasCount: Int): Boolean =
        (action == null || action == ACTION_MAIN) && !hasData && extrasCount == 0

    /**
     * Resolves the remembered start view when the user has no explicit default.
     * HOME is only valid while the new layout is selected; classic users get
     * [fallbackView] instead, so their start screen does not change.
     */
    @JvmStatic
    fun resolveLastView(
        lastView: Int,
        homeViewType: Int,
        newHomeSelected: Boolean,
        fallbackView: Int
    ): Int = if (lastView == homeViewType && !newHomeSelected) fallbackView else lastView
}
