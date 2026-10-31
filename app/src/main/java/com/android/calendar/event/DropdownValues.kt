/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.event

/**
 * Pure mapping helpers for dropdown options. Kept free of Android types so the
 * rules can run as plain JVM unit tests.
 *
 * Options are always resolved by index into the list the dropdown was built
 * from, and stored values are always looked up by value, never by display
 * text or by a filtered position.
 */
object DropdownValues {

    /** The value of the option at [index], or null if [index] is not a valid option. */
    @JvmStatic
    fun <T> valueAt(values: List<T>, index: Int): T? =
        if (index in values.indices) values[index] else null

    /**
     * Index of [value] in [values], or [fallback] when the value is not offered.
     * Lets a stored value that is no longer offered still show a valid option.
     */
    @JvmStatic
    fun indexOfOrFallback(values: List<Int>, value: Int, fallback: Int): Int {
        val index = values.indexOf(value)
        return if (index >= 0) index else fallback
    }
}
