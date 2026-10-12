/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.theme

import com.android.calendar.theme.model.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRulesTest {

    @Test
    fun blackThemeAlwaysUsesTheBlackBackground() {
        assertTrue(ThemeRules.usesBlackBackground(Theme.BLACK, pureBlack = false, systemDark = false))
    }

    @Test
    fun darkThemeUsesBlackOnlyWithPureBlackOn() {
        assertFalse(ThemeRules.usesBlackBackground(Theme.DARK, pureBlack = false, systemDark = true))
        assertTrue(ThemeRules.usesBlackBackground(Theme.DARK, pureBlack = true, systemDark = false))
    }

    @Test
    fun systemThemeNeedsDarkSystemAndPureBlack() {
        assertTrue(ThemeRules.usesBlackBackground(Theme.SYSTEM, pureBlack = true, systemDark = true))
        assertFalse(ThemeRules.usesBlackBackground(Theme.SYSTEM, pureBlack = true, systemDark = false))
        assertFalse(ThemeRules.usesBlackBackground(Theme.SYSTEM, pureBlack = false, systemDark = true))
    }

    @Test
    fun lightThemeNeverUsesBlack() {
        assertFalse(ThemeRules.usesBlackBackground(Theme.LIGHT, pureBlack = true, systemDark = true))
    }

    @Test
    fun pureBlackSwitchChangesTheKeyEvenWithTheSameSystemMode() {
        // Same uiMode (dark) before and after the switch: only the pure black preference differs.
        val before = ThemeRules.effectiveKey(Theme.DARK, pureBlack = false, systemDark = true, colorName = "TEAL")
        val after = ThemeRules.effectiveKey(Theme.DARK, pureBlack = true, systemDark = true, colorName = "TEAL")
        assertNotEquals(before, after)
    }

    @Test
    fun themeChangeAndSystemModeChangeBothChangeTheKey() {
        val base = ThemeRules.effectiveKey(Theme.LIGHT, pureBlack = false, systemDark = false, colorName = "TEAL")
        assertNotEquals(base, ThemeRules.effectiveKey(Theme.DARK, false, false, "TEAL"))
        assertNotEquals(base, ThemeRules.effectiveKey(Theme.LIGHT, false, true, "TEAL"))
    }

    @Test
    fun unchangedConfigurationGivesTheSameKey() {
        assertEquals(
            ThemeRules.effectiveKey(Theme.BLACK, true, true, "MONET"),
            ThemeRules.effectiveKey(Theme.BLACK, true, true, "MONET")
        )
    }
}
