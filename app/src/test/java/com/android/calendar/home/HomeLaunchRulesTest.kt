/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLaunchRulesTest {

    @Test
    fun onlyExplicitNewValueSelectsNewHome() {
        assertTrue(HomeLaunchRules.isNewHomeSelected("new"))
        assertFalse(HomeLaunchRules.isNewHomeSelected("classic"))
        assertFalse(HomeLaunchRules.isNewHomeSelected(null))
        assertFalse(HomeLaunchRules.isNewHomeSelected(""))
        assertFalse(HomeLaunchRules.isNewHomeSelected("NEW"))
    }

    @Test
    fun launcherStartIsPlain() {
        assertTrue(HomeLaunchRules.isPlainLaunch("android.intent.action.MAIN", false, 0))
        assertTrue(HomeLaunchRules.isPlainLaunch(null, false, 0))
    }

    @Test
    fun notificationsWidgetsAndExternalIntentsAreNotPlain() {
        // ACTION_VIEW with a data URI (widget, external event link).
        assertFalse(HomeLaunchRules.isPlainLaunch("android.intent.action.VIEW", true, 0))
        // Event begin-time extra (notification / external intent).
        assertFalse(HomeLaunchRules.isPlainLaunch("android.intent.action.VIEW", false, 2))
        // ACTION_EDIT always opens the editor.
        assertFalse(HomeLaunchRules.isPlainLaunch("android.intent.action.EDIT", true, 0))
        // Any MAIN launch that carries extras is treated as targeted.
        assertFalse(HomeLaunchRules.isPlainLaunch("android.intent.action.MAIN", false, 1))
    }

    @Test
    fun lastViewHomeIsOnlyKeptWhileNewLayoutIsSelected() {
        val home = 6
        val week = 3
        // New layout keeps the remembered home view.
        assertEquals(home, HomeLaunchRules.resolveLastView(home, home, true, week))
        // Classic users never land on the home view; they get the fallback.
        assertEquals(week, HomeLaunchRules.resolveLastView(home, home, false, week))
        // Other remembered views are untouched in both modes, so classic is unchanged.
        assertEquals(1, HomeLaunchRules.resolveLastView(1, home, false, week))
        assertEquals(1, HomeLaunchRules.resolveLastView(1, home, true, week))
    }

    @Test
    fun manualToggle_persistsAsStoredValue() {
        // The preference stores "classic" or "new"; any other value means classic.
        val stored = listOf("new", "classic", null, "bogus")
        val selected = stored.map { HomeLaunchRules.isNewHomeSelected(it) }
        assertEquals(listOf(true, false, false, false), selected)
    }
}
