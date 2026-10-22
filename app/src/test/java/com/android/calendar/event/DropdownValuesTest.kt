/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropdownValuesTest {

    @Test
    fun valueAtReturnsOptionForValidIndex() {
        assertEquals(15, DropdownValues.valueAt(listOf(0, 5, 15), 2))
    }

    @Test
    fun valueAtRejectsNegativeAndOutOfRangeIndexes() {
        assertNull(DropdownValues.valueAt(listOf(0, 5), -1))
        assertNull(DropdownValues.valueAt(listOf(0, 5), 2))
        assertNull(DropdownValues.valueAt(emptyList<Int>(), 0))
    }

    @Test
    fun zeroMinutesIsAValidOption() {
        // 0 minutes ("at start") must not be confused with "no selection".
        assertEquals(0, DropdownValues.valueAt(listOf(0, 1, 5), 0))
    }

    @Test
    fun indexOfOrFallbackFindsValueByValueNotPosition() {
        // Visibility-style values where the value differs from its position.
        val offered = listOf(0, 2, 3)
        assertEquals(1, DropdownValues.indexOfOrFallback(offered, 2, 0))
        assertEquals(2, DropdownValues.indexOfOrFallback(offered, 3, 0))
    }

    @Test
    fun indexOfOrFallbackUsesFallbackWhenValueNotOffered() {
        assertEquals(0, DropdownValues.indexOfOrFallback(listOf(1, 2), 9, 0))
    }

    @Test
    fun calendarLabelAddsOwnerOnlyWhenItDiffers() {
        assertEquals("Work (a@example.com)", DropdownValues.calendarLabel("Work", "a@example.com"))
        assertEquals("a@example.com", DropdownValues.calendarLabel("a@example.com", "a@example.com"))
        assertEquals("Local", DropdownValues.calendarLabel("Local", ""))
        assertEquals("Local", DropdownValues.calendarLabel("Local", null))
    }

    @Test
    fun calendarLabelHandlesMissingName() {
        assertEquals("", DropdownValues.calendarLabel(null, null))
    }
}
