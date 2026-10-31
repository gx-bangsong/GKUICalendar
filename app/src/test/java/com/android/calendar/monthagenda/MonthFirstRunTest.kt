/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First-run month default. The decision uses the keys that existed before any default was
 * written, plus the UI language. It is idempotent through the marker.
 */
class MonthFirstRunTest {

    private val marker = MonthFirstRun.KEY_MARKER

    @Test
    fun freshInstallInChineseStartsInMonthWithAgendaLayout() {
        val plan = MonthFirstRun.plan(emptySet(), "zh")
        assertTrue(plan.applyChineseDefault)
        assertTrue(plan.writeMarker)
    }

    @Test
    fun freshInstallInEnglishKeepsTheExistingDefault() {
        val plan = MonthFirstRun.plan(emptySet(), "en")
        assertFalse(plan.applyChineseDefault)
        assertTrue(plan.writeMarker)
    }

    @Test
    fun freshInstallWithUnknownLanguageKeepsTheExistingDefault() {
        assertFalse(MonthFirstRun.plan(emptySet(), null).applyChineseDefault)
    }

    @Test
    fun onlyTheBareZhLanguageMatches() {
        // The language is compared as the bare tag. Region is never used to infer the language.
        assertTrue(MonthFirstRun.plan(emptySet(), "zh").applyChineseDefault)
        assertFalse(MonthFirstRun.plan(emptySet(), "ja").applyChineseDefault)
    }

    @Test
    fun upgradedInstallKeepsItsStateEvenInChinese() {
        // An install from an earlier version already has the version key and other keys.
        val existing = setOf("spv", "preferred_startView", "preferences_default_start")
        val plan = MonthFirstRun.plan(existing, "zh")
        assertFalse(plan.applyChineseDefault)
        assertTrue(plan.writeMarker)
    }

    @Test
    fun restoredBackupKeepsItsChoicesEvenInChinese() {
        // A restored backup brings its keys back before the app starts.
        val restored = setOf("preferences_month_layout", "preferred_startView")
        assertFalse(MonthFirstRun.plan(restored, "zh").applyChineseDefault)
    }

    @Test
    fun explicitLayoutChoiceWithNoOtherKeysIsKept() {
        // Only one choice was stored, so the install is not fresh.
        assertFalse(MonthFirstRun.plan(setOf("preferences_month_layout"), "zh").applyChineseDefault)
    }

    @Test
    fun legacyRound1ChoiceIsKept() {
        assertFalse(MonthFirstRun.plan(setOf("preferences_home_layout"), "zh").applyChineseDefault)
    }

    @Test
    fun markerMakesTheDecisionRunOnce() {
        val afterFirstRun = setOf(marker, "spv", "preferences_month_layout", "preferred_startView")
        val plan = MonthFirstRun.plan(afterFirstRun, "zh")
        assertFalse(plan.applyChineseDefault)
        assertFalse(plan.writeMarker)
    }

    @Test
    fun userClassicChoiceSurvivesARestart() {
        // The first run wrote agenda and the marker. The user then picks classic. A restart must
        // not write agenda again.
        val keysAfterChoice = setOf(marker, "spv", "preferences_month_layout", "preferred_startView")
        assertFalse(MonthFirstRun.plan(keysAfterChoice, "zh").applyChineseDefault)
    }

    @Test
    fun userStartViewChoiceSurvivesAnUpgrade() {
        // Marker is written, so a later version never sets the Chinese defaults again.
        val keys = setOf(marker, "spv", "preferred_startView")
        assertFalse(MonthFirstRun.plan(keys, "zh").applyChineseDefault)
    }

    @Test
    fun markerAloneIsNotAFreshInstallSignal() {
        // Marker absent but keys present means existing state, not a new install.
        assertFalse(MonthFirstRun.plan(setOf("spv"), "zh").applyChineseDefault)
    }

    @Test
    fun firstThenSecondStartGivesOneApplyAndNoRewrite() {
        val first = MonthFirstRun.plan(emptySet(), "zh")
        assertTrue(first.applyChineseDefault)
        // After the first write, the stored keys include the marker and the written values.
        val afterWrite = setOf(
            marker, "spv", "preferences_month_layout", "preferences_default_start",
            "preferred_startView"
        )
        val second = MonthFirstRun.plan(afterWrite, "zh")
        assertFalse(second.applyChineseDefault)
        assertFalse(second.writeMarker)
    }

    @Test
    fun startViewValueIsTheMonthEntry() {
        assertEquals(4, MonthLayoutRules.VIEW_MONTH)
        assertEquals(MonthLayoutRules.LAYOUT_AGENDA, "agenda")
    }
}
