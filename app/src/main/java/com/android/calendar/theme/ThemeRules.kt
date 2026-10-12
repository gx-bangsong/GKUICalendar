/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.theme

import com.android.calendar.theme.model.Theme

/**
 * Pure rules for the effective theme. This file has no Android types, so JVM tests can check
 * the decisions that [applyThemeAndPrimaryColor] and the open-screen check depend on.
 */
object ThemeRules {

    /**
     * True when the window uses the pure black (AMOLED) background. Black always does. Dark does
     * when pure black is on. System follows the system night mode, and needs pure black too.
     */
    @JvmStatic
    fun usesBlackBackground(theme: Theme, pureBlack: Boolean, systemDark: Boolean): Boolean =
        when (theme) {
            Theme.BLACK -> true
            Theme.DARK -> pureBlack
            Theme.SYSTEM -> pureBlack && systemDark
            Theme.LIGHT -> false
        }

    /**
     * Every input that decides the look of an open screen. The system uiMode alone is not
     * enough: the pure black switch and the app theme can change without a uiMode change.
     */
    @JvmStatic
    fun effectiveKey(theme: Theme, pureBlack: Boolean, systemDark: Boolean, colorName: String): String =
        listOf(theme.name, pureBlack.toString(), systemDark.toString(), colorName).joinToString("|")
}
