/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription

/** How a [CellInfo]'s badge is rendered. */
enum class BgStyle {
    /** Plain text, no background (default). */
    NONE,
    /** Small filled chip behind the text (festival/shift/traffic). */
    CHIP,
    /** Stroked ring around the solar day number (reserved for future use). */
    RING
}
