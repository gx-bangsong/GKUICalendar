/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

import java.util.concurrent.atomic.AtomicInteger

/**
 * Drops results of superseded requests. [counter] is shared with
 * Event.loadEvents, which also returns early when a newer request exists.
 */
class RequestGate {
    val counter = AtomicInteger(0)

    /** Starts a new request. Every earlier token becomes stale. */
    fun begin(): Int = counter.incrementAndGet()

    fun isCurrent(token: Int): Boolean = counter.get() == token
}
