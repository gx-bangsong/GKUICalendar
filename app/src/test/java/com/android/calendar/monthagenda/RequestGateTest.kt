/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestGateTest {

    @Test
    fun onlyTheNewestRequestIsCurrent() {
        val gate = RequestGate()
        val first = gate.begin()
        assertTrue(gate.isCurrent(first))

        val second = gate.begin()
        assertFalse("superseded request must be dropped", gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun rapidSelectionsLeaveOnlyTheLastToken() {
        val gate = RequestGate()
        val tokens = (1..50).map { gate.begin() }
        val current = tokens.filter { gate.isCurrent(it) }
        assertEquals(listOf(50), current)
    }

    @Test
    fun sharedCounterMatchesEventLoaderContract() {
        // Event.loadEvents returns early when requestId != sequenceNumber.get().
        val gate = RequestGate()
        val token = gate.begin()
        assertEquals(token, gate.counter.get())
        gate.begin()
        assertFalse(token == gate.counter.get())
    }
}
