/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.period

import com.android.calendar.subscription.period.data.PeriodEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodEngineTest {

    private val anchor = 1000
    private val cycle = 28
    private val length = 5

    @Test fun anchorDayIsPeriod() {
        assertEquals(PeriodEngine.PERIOD, PeriodEngine.phaseFor(anchor, cycle, length, anchor))
        assertEquals(PeriodEngine.PERIOD, PeriodEngine.phaseFor(anchor, cycle, length, anchor + 4))
    }

    @Test fun dayAfterPeriodIsNeutral() {
        assertEquals(PeriodEngine.NONE, PeriodEngine.phaseFor(anchor, cycle, length, anchor + 5))
    }

    @Test fun ovulationIsFourteenDaysBeforeNextStart() {
        // cycle 28 → ovulation at index 14
        assertEquals(PeriodEngine.OVULATION,
            PeriodEngine.phaseFor(anchor, cycle, length, anchor + 14))
    }

    @Test fun fertileWindowSurroundsOvulation() {
        assertEquals(PeriodEngine.FERTILE, PeriodEngine.phaseFor(anchor, cycle, length, anchor + 9))
        assertEquals(PeriodEngine.FERTILE, PeriodEngine.phaseFor(anchor, cycle, length, anchor + 13))
        assertEquals(PeriodEngine.FERTILE, PeriodEngine.phaseFor(anchor, cycle, length, anchor + 15))
        assertEquals(PeriodEngine.NONE, PeriodEngine.phaseFor(anchor, cycle, length, anchor + 16))
    }

    @Test fun nextCycleStartIsPredicted() {
        assertEquals(PeriodEngine.PREDICTED_START,
            PeriodEngine.phaseFor(anchor, cycle, length, anchor + cycle))
        assertEquals(PeriodEngine.PERIOD,
            PeriodEngine.phaseFor(anchor, cycle, length, anchor + cycle + 1))
    }

    @Test fun beforeAnchorIsNone() {
        assertEquals(PeriodEngine.NONE, PeriodEngine.phaseFor(anchor, cycle, length, anchor - 1))
    }

    @Test fun nextStartAndCountdown() {
        assertEquals(anchor + cycle, PeriodEngine.nextPeriodStart(anchor, cycle, anchor + 1))
        assertEquals(anchor, PeriodEngine.nextPeriodStart(anchor, cycle, anchor))
        assertEquals(cycle - 3, PeriodEngine.daysUntilNextStart(anchor, cycle, anchor + 3))
    }

    @Test fun lengthsAreClamped() {
        assertEquals(PeriodEngine.MIN_CYCLE_LENGTH, PeriodEngine.clampCycleLength(1))
        assertEquals(PeriodEngine.MAX_CYCLE_LENGTH, PeriodEngine.clampCycleLength(99))
        assertEquals(PeriodEngine.MIN_PERIOD_LENGTH, PeriodEngine.clampPeriodLength(0))
        assertEquals(PeriodEngine.MAX_PERIOD_LENGTH, PeriodEngine.clampPeriodLength(30))
    }
}
