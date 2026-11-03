/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRegistryTest {
    @Test fun bgStyle_hasThreeValues() {
        assertEquals(3, BgStyle.values().size)
    }

    @Test fun providersList_containsEveryBuiltInSubscription() {
        val ids = HashSet<String>()
        for (p in SubscriptionRegistry.getAll()) ids.add(p.id)
        assertEquals(
            setOf("lunar", "shift", "traffic", "period", "lunar_birthday"), ids)
    }

    @Test fun providerPrioritiesAreUnique() {
        val seen = HashSet<Int>()
        for (p in SubscriptionRegistry.getAll()) {
            assertTrue("duplicate priority " + p.priority, seen.add(p.priority))
        }
    }

    @Test fun cellInfo_primaryTextPreserved() {
        val ci = CellInfo("shift", "\u65e9")
        assertEquals("\u65e9", ci.primaryText)
        assertEquals(BgStyle.NONE, ci.backgroundStyle)
    }
}
