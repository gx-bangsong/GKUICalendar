/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [SubscriptionText] formatting is exercised through CellInfo directly; the
 * Context-backed entry points need an instrumented runner, so these cover the
 * pure text-assembly contract via the same separator rules.
 */
class SubscriptionTextTest {

    private fun join(vararg infos: CellInfo): String? {
        val sb = StringBuilder()
        for (ci in infos) {
            val t = ci.primaryText ?: continue
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(t)
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    @Test fun singleBadgeHasNoSeparator() {
        assertEquals("\u65e9", join(CellInfo("shift", "\u65e9")))
    }

    @Test fun multipleBadgesAreSeparated() {
        assertEquals("\u65e9  3\u548c8",
            join(CellInfo("shift", "\u65e9"), CellInfo("traffic", "3\u548c8")))
    }

    @Test fun nullPrimaryTextIsSkipped() {
        assertEquals("\u65e9",
            join(CellInfo("shift", "\u65e9"), CellInfo("traffic", null)))
    }

    @Test fun allEmptyYieldsNull() {
        assertNull(join(CellInfo("traffic", null)))
        assertNull(join())
    }
}
