/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import android.content.SharedPreferences

/**
 * Rules for the single 月 (month) entry. The month entry has two layouts, chosen in
 * the 月视图布局 setting: the classic month grid, or 月历＋日程 (month grid plus the
 * selected day's schedule). The layout is not a navigation view of its own.
 *
 * Pure logic lives here so that it can be unit-tested without Android.
 */
object MonthLayoutRules {

    /** External view id of the month entry. Unchanged; DAY/WEEK/MONTH/AGENDA are not renumbered. */
    const val VIEW_MONTH = 4

    /** View id that round 1 used for a separate home screen. Only read for legacy values. */
    const val LEGACY_HOME_VIEW = 6

    /** Layout value for the classic month grid. */
    const val LAYOUT_CLASSIC = "classic"

    /** Layout value for 月历＋日程 inside the month entry. */
    const val LAYOUT_AGENDA = "agenda"

    /** Current setting key (月视图布局). */
    const val KEY_MONTH_LAYOUT = "preferences_month_layout"

    /** Round-1 local preference key. Migrated once, then removed. */
    const val KEY_LEGACY_HOME_LAYOUT = "preferences_home_layout"

    /** Round-1 value meaning "use the month and day screen". */
    const val LEGACY_LAYOUT_NEW = "new"

    /** Round-1 value meaning "classic". */
    const val LEGACY_LAYOUT_CLASSIC = "classic"

    /** Maps the legacy HOME view id to the month entry. Every other value is unchanged. */
    @JvmStatic
    fun normalizeViewType(viewType: Int): Int =
        if (viewType == LEGACY_HOME_VIEW) VIEW_MONTH else viewType

    /**
     * Resolves the month layout from stored values.
     *
     * @param newValue the current setting, or null when it was never written.
     * @param legacyValue the round-1 preference value, or null when absent.
     * @param storedLegacyHomeView true when the saved start view was the legacy HOME id.
     * @return [LAYOUT_CLASSIC] or [LAYOUT_AGENDA], or null when there is no evidence of a choice.
     */
    @JvmStatic
    fun resolveLayout(newValue: String?, legacyValue: String?, storedLegacyHomeView: Boolean): String? {
        if (newValue == LAYOUT_CLASSIC || newValue == LAYOUT_AGENDA) return newValue
        return when {
            // An explicit round-1 choice is kept as it was.
            legacyValue == LEGACY_LAYOUT_NEW -> LAYOUT_AGENDA
            legacyValue == LEGACY_LAYOUT_CLASSIC -> LAYOUT_CLASSIC
            // Saved HOME state without any explicit choice maps to the month entry with 月历＋日程.
            storedLegacyHomeView -> LAYOUT_AGENDA
            else -> null
        }
    }

    /** True when the month entry should show 月历＋日程. An unset value means classic. */
    @JvmStatic
    fun isAgendaLayout(storedValue: String?): Boolean = storedValue == LAYOUT_AGENDA

    /**
     * Writes the new layout key from legacy evidence, then removes the legacy key.
     * Only the two keys above are touched. Nothing is written without evidence,
     * so a fresh install keeps the default.
     */
    @JvmStatic
    fun migrate(prefs: SharedPreferences, storedStartView: Int) {
        val legacy = prefs.getString(KEY_LEGACY_HOME_LAYOUT, null)
        val hasNew = prefs.contains(KEY_MONTH_LAYOUT)
        val editor = prefs.edit()
        var changed = false
        if (!hasNew) {
            val resolved = resolveLayout(
                null, legacy, storedStartView == LEGACY_HOME_VIEW
            )
            if (resolved != null) {
                editor.putString(KEY_MONTH_LAYOUT, resolved)
                changed = true
            }
        }
        if (legacy != null) {
            editor.remove(KEY_LEGACY_HOME_LAYOUT)
            changed = true
        }
        if (changed) editor.apply()
    }
}
