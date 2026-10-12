/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expand animation uses a [RequestGate] token. Cancelling advances the gate, so a cancelled
 * animator's end callback is ignored, and a new animation's token stays valid.
 */
class ExpandGateTest {

    @Test
    fun cancelMakesTheRunningAnimationStale() {
        val gate = RequestGate()
        val running = gate.begin()
        assertTrue(gate.isCurrent(running))
        gate.begin() // cancelExpandAnimation()
        assertFalse(gate.isCurrent(running))
    }

    @Test
    fun aNewAnimationAfterCancelIsCurrentAndOldOnesAreNot() {
        val gate = RequestGate()
        val first = gate.begin()
        gate.begin() // cancel while the first one runs (takeover)
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun aQuickReverseAfterAnimationEndIsStillCurrent() {
        // Natural end: the token is still current, so the end callback lands on the target.
        val gate = RequestGate()
        val token = gate.begin()
        assertTrue(gate.isCurrent(token))
    }
}
