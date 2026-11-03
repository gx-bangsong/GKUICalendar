/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.event

import android.content.Context
import android.util.AttributeSet
import android.widget.AdapterView
import com.google.android.material.textfield.MaterialAutoCompleteTextView

/**
 * A pure-selection MD3 exposed dropdown, used inside a
 * `TextInputLayout` with the `ExposedDropdownMenu` style.
 *
 * The option list is fixed by [setChoices]. The chosen option is tracked by
 * its **index** into that list ([selectedIndex]); callers map the index to
 * their own values and never parse the display text. The field is not
 * typeable (`inputType="none"`), so its text is only the current label.
 */
class ExposedDropdownField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialAutoCompleteTextView(context, attrs) {

    /** Receives the new index when the user picks a *different* option. */
    fun interface OnPickListener {
        fun onPick(index: Int)
    }

    private var choices: List<String> = emptyList()

    /** Index of the current option in the list passed to [setChoices], or -1. */
    var selectedIndex: Int = -1
        private set

    /** Notified only for user picks, never for programmatic [select]. */
    var onPickListener: OnPickListener? = null

    init {
        setOnItemClickListener(AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position != selectedIndex) {
                selectedIndex = position
                onPickListener?.onPick(position)
            }
        })
    }

    /**
     * Replaces the options and selects [index] (or nothing if it is out of
     * range). Does not notify [onPickListener].
     */
    fun setChoices(labels: List<String>, index: Int) {
        choices = labels.toList()
        setSimpleItems(choices.toTypedArray())
        select(index)
    }

    /** Shows option [index] without notifying [onPickListener]. */
    fun select(index: Int) {
        selectedIndex = if (index in choices.indices) index else -1
        // filter=false: do not run the autocomplete filter on the label text.
        setText(choices.getOrNull(selectedIndex).orEmpty(), false)
    }
}
