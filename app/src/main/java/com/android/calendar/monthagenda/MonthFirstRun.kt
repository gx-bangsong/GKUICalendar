/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.android.calendar.settings.GeneralPreferences

/**
 * First-run default for the month entry. A new install whose app UI language is Chinese (zh)
 * starts in MONTH with 月历＋日程. Every other case keeps the existing defaults.
 *
 * The decision uses the preference keys that existed before any default was written, so an
 * install with no state is told apart from an upgrade, a restored backup or an explicit choice.
 * The marker only makes the decision idempotent; it is never used alone to detect a new install.
 * Nothing here runs again once the marker is written, so later user choices are not overwritten.
 */
object MonthFirstRun {

    /** Marker that the first-run decision has been made. Idempotency only. */
    const val KEY_MARKER = "month_first_run_v1"

    /** BCP-47 language of the Chinese UI. Region tags such as zh-CN or zh-TW both match. */
    const val LANGUAGE_CHINESE = "zh"

    /**
     * What to do on this start.
     *
     * @property applyChineseDefault write the 月历＋日程 layout and the MONTH start view.
     * @property writeMarker record that the decision was made.
     */
    data class Plan(val applyChineseDefault: Boolean, val writeMarker: Boolean)

    /**
     * Pure decision, testable without Android.
     *
     * @param existingKeys preference keys present before any default was written.
     * @param uiLanguage the app's effective UI language, or null when unknown.
     */
    @JvmStatic
    fun plan(existingKeys: Set<String>, uiLanguage: String?): Plan {
        if (KEY_MARKER in existingKeys) return Plan(applyChineseDefault = false, writeMarker = false)
        val freshInstall = existingKeys.isEmpty()
        return Plan(
            applyChineseDefault = freshInstall && uiLanguage == LANGUAGE_CHINESE,
            writeMarker = true
        )
    }

    /**
     * The language the app actually displays, from the resources configuration. This follows a
     * per-app language when one is set. Location, IP and time zone are not used.
     */
    @JvmStatic
    fun uiLanguage(context: Context): String? {
        val config = context.resources.configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (config.locales.size() > 0) config.locales.get(0) else null
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
        return locale?.language
    }

    /**
     * Applies the plan in one write. Call it after the existing default writes, with the keys
     * recorded before them.
     */
    @JvmStatic
    fun initialize(prefs: SharedPreferences, existingKeys: Set<String>, uiLanguage: String?) {
        val decision = plan(existingKeys, uiLanguage)
        if (!decision.applyChineseDefault && !decision.writeMarker) return
        val editor = prefs.edit()
        if (decision.applyChineseDefault) {
            editor.putString(MonthLayoutRules.KEY_MONTH_LAYOUT, MonthLayoutRules.LAYOUT_AGENDA)
            editor.putInt(GeneralPreferences.KEY_START_VIEW, MonthLayoutRules.VIEW_MONTH)
        }
        if (decision.writeMarker) {
            editor.putBoolean(KEY_MARKER, true)
        }
        editor.apply()
    }
}
