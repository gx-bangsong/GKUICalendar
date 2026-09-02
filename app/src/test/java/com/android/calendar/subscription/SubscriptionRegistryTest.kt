/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubscriptionRegistryTest {
    @Test fun bgStyle_hasThreeValues() {
        assertEquals(3, BgStyle.values().size)
    }

    @Test fun providersList_containsLunarAndShift() {
        val ids = SubscriptionRegistry.getAll().map { it.id }.toSet()
        assertEquals(setOf("lunar", "shift"), ids)
    }

    @Test fun cellInfo_primaryTextPreserved() {
        val ci = CellInfo("shift", "\u65e9")
        assertEquals("\u65e9", ci.primaryText)
        assertEquals(BgStyle.NONE, ci.backgroundStyle)
    }
}
