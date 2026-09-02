/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.shift.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.shift.ShiftProvider
import com.android.calendar.subscription.shift.data.ShiftEngine
import com.android.calendar.subscription.shift.data.ShiftPresets
import ws.xsoh.etar.R
import java.util.Locale
import java.util.TimeZone

/**
 * Minimal settings screen for [ShiftProvider]. Phase 1b offers three quick
 * preset buttons (三班倒 / 四班三倒 / 上二休二), a disable button, and a
 * 14-day preview. Full pattern editing comes in Phase 2. Large screens
 * (layout-sw600dp) get a two-column preset grid.
 */
class ShiftSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_shift_settings, container, false)
        val status: TextView = root.findViewById(R.id.shift_status)
        val presetThree: Button = root.findViewById(R.id.btn_preset_three)
        val presetFour: Button = root.findViewById(R.id.btn_preset_fourthree)
        val presetTwoOff: Button = root.findViewById(R.id.btn_preset_two_on_two_off)
        val disable: Button = root.findViewById(R.id.btn_disable)
        val preview: TextView = root.findViewById(R.id.shift_preview)

        val ctx = requireContext()
        fun refresh() {
            val sum = ShiftProvider.getCurrentSummary(ctx)
            if (sum == null) {
                status.text = getString(R.string.sub_shift_state_disabled)
                preview.text = ""
            } else {
                status.text = sum
                preview.text = buildPreview(ctx)
            }
        }
        refresh()

        presetThree.setOnClickListener {
            ShiftProvider.applyPresetToday(ctx, ShiftPresets.KEY_THREE); refresh() }
        presetFour.setOnClickListener {
            ShiftProvider.applyPresetToday(ctx, ShiftPresets.KEY_FOUR_THREE); refresh() }
        presetTwoOff.setOnClickListener {
            ShiftProvider.applyPresetToday(ctx, ShiftPresets.KEY_TWO_ON_TWO_OFF); refresh() }
        disable.setOnClickListener { ShiftProvider.onDisabled(ctx); refresh() }
        activity?.title = getString(R.string.sub_shift_name)
        return root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sub_shift_name)
    }

    private fun buildPreview(ctx: Context): String {
        val (cycle, anchor) = ShiftProvider.currentState(ctx) ?: return ""
        val tz = TimeZone.getDefault()
        val todayJd = Time.getJulianDay(System.currentTimeMillis(),
            tz.getOffset(System.currentTimeMillis()) / 1000L)
        val t = Time(tz.id)
        val sb = StringBuilder()
        val dayNames = arrayOf("", "\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c",
            "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d")
        for (i in 0 until 14) {
            val jd = todayJd + i
            val type = ShiftEngine.typeFor(cycle, anchor, jd)
            val label = if (type < 0) "-" else ShiftEngine.labelFor(type)
            t.setJulianDay(jd)
            t.normalize()
            val wd = t.getWeekDay().coerceIn(1, 7)
            sb.append(String.format(Locale.getDefault(),
                "%d/%d %s  %s\n", t.getMonth() + 1, t.getDay(), dayNames[wd], label))
        }
        return sb.toString().trimEnd()
    }
}
