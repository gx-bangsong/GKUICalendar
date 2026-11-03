/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthLayoutRulesTest {

    @Test
    fun externalViewIdsAreNotRenumbered() {
        assertEquals(0, MonthLayoutRules.normalizeViewType(0))
        assertEquals(1, MonthLayoutRules.normalizeViewType(1))
        assertEquals(2, MonthLayoutRules.normalizeViewType(2))
        assertEquals(3, MonthLayoutRules.normalizeViewType(3))
        assertEquals(4, MonthLayoutRules.normalizeViewType(4))
        assertEquals(5, MonthLayoutRules.normalizeViewType(5))
    }

    @Test
    fun legacyHomeIdMapsToMonth() {
        assertEquals(MonthLayoutRules.VIEW_MONTH, MonthLayoutRules.normalizeViewType(6))
        assertEquals(4, MonthLayoutRules.normalizeViewType(6))
    }

    @Test
    fun explicitNewValueWins() {
        assertEquals("classic", MonthLayoutRules.resolveLayout("classic", "new", true))
        assertEquals("agenda", MonthLayoutRules.resolveLayout("agenda", "classic", false))
    }

    @Test
    fun legacyNewChoiceBecomesMonthAgenda() {
        assertEquals("agenda", MonthLayoutRules.resolveLayout(null, "new", false))
    }

    @Test
    fun legacyClassicChoiceIsKept() {
        assertEquals("classic", MonthLayoutRules.resolveLayout(null, "classic", false))
        // An explicit classic choice is not overridden by a saved HOME id.
        assertEquals("classic", MonthLayoutRules.resolveLayout(null, "classic", true))
    }

    @Test
    fun savedLegacyHomeWithoutChoiceBecomesMonthAgenda() {
        assertEquals("agenda", MonthLayoutRules.resolveLayout(null, null, true))
    }

    @Test
    fun noEvidenceLeavesTheDefault() {
        assertNull(MonthLayoutRules.resolveLayout(null, null, false))
    }

    @Test
    fun onlyAgendaValueSelectsAgendaLayout() {
        assertTrue(MonthLayoutRules.isAgendaLayout("agenda"))
        assertFalse(MonthLayoutRules.isAgendaLayout("classic"))
        assertFalse(MonthLayoutRules.isAgendaLayout(null))
        assertFalse(MonthLayoutRules.isAgendaLayout("new"))
    }
}
