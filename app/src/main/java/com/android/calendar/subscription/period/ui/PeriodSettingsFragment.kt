/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.period.ui

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.period.PeriodProvider
import com.android.calendar.subscription.period.data.PeriodEngine
import ws.xsoh.etar.R
import java.util.Locale
import java.util.TimeZone

/**
 * Settings for the 生理期 subscription: last-period start date picker, cycle
 * and bleeding-length steppers, a 30-day phase preview and a disable button.
 */
class PeriodSettingsFragment : Fragment() {

    private lateinit var status: TextView
    private lateinit var anchorValue: TextView
    private lateinit var cycleValue: TextView
    private lateinit var lengthValue: TextView
    private lateinit var preview: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_period_settings, container, false)
        val ctx = requireContext()
        status = root.findViewById(R.id.period_status)
        anchorValue = root.findViewById(R.id.period_anchor_value)
        cycleValue = root.findViewById(R.id.period_cycle_value)
        lengthValue = root.findViewById(R.id.period_length_value)
        preview = root.findViewById(R.id.period_preview)

        root.findViewById<View>(R.id.period_anchor_row).setOnClickListener { pickAnchor(ctx) }
        root.findViewById<View>(R.id.btn_cycle_minus).setOnClickListener {
            PeriodProvider.setCycleLength(ctx, PeriodProvider.getCycleLength(ctx) - 1); refresh()
        }
        root.findViewById<View>(R.id.btn_cycle_plus).setOnClickListener {
            PeriodProvider.setCycleLength(ctx, PeriodProvider.getCycleLength(ctx) + 1); refresh()
        }
        root.findViewById<View>(R.id.btn_length_minus).setOnClickListener {
            PeriodProvider.setPeriodLength(ctx, PeriodProvider.getPeriodLength(ctx) - 1); refresh()
        }
        root.findViewById<View>(R.id.btn_length_plus).setOnClickListener {
            PeriodProvider.setPeriodLength(ctx, PeriodProvider.getPeriodLength(ctx) + 1); refresh()
        }
        root.findViewById<View>(R.id.btn_disable).setOnClickListener {
            PeriodProvider.onDisabled(ctx); refresh()
        }

        refresh()
        activity?.title = getString(R.string.sub_period_name)
        return root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sub_period_name)
    }

    private fun pickAnchor(ctx: Context) {
        val t = Time(TimeZone.getDefault().id)
        val current = PeriodProvider.getAnchor(ctx)
        t.setJulianDay(if (current == PeriodProvider.NO_ANCHOR)
            PeriodProvider.todayJulianDay() else current)
        t.normalize()
        DatePickerDialog(ctx, { _, y, m, d ->
            val picked = Time(TimeZone.getDefault().id)
            picked.set(d, m, y)
            val millis = picked.normalize()
            PeriodProvider.setAnchor(ctx, Time.getJulianDay(
                millis, TimeZone.getDefault().getOffset(millis) / 1000L))
            refresh()
        }, t.getYear(), t.getMonth(), t.getDay()).show()
    }

    private fun refresh() {
        val ctx = requireContext()
        status.text = PeriodProvider.getCurrentSummary(ctx)
            ?: getString(R.string.sub_period_state_disabled)
        val anchor = PeriodProvider.getAnchor(ctx)
        anchorValue.text = if (anchor == PeriodProvider.NO_ANCHOR)
            getString(R.string.sub_period_anchor_unset) else formatDate(anchor)
        cycleValue.text = getString(
            R.string.sub_days_fmt, PeriodProvider.getCycleLength(ctx))
        lengthValue.text = getString(
            R.string.sub_days_fmt, PeriodProvider.getPeriodLength(ctx))
        preview.text = buildPreview(ctx)
    }

    private fun formatDate(jd: Int): String {
        val t = Time(TimeZone.getDefault().id)
        t.setJulianDay(jd)
        t.normalize()
        return getString(R.string.sub_shift_anchor_date_fmt,
            t.getYear(), t.getMonth() + 1, t.getDay())
    }

    private fun buildPreview(ctx: Context): String {
        val anchor = PeriodProvider.getAnchor(ctx)
        if (anchor == PeriodProvider.NO_ANCHOR) return getString(R.string.sub_period_anchor_hint)
        val cycle = PeriodProvider.getCycleLength(ctx)
        val length = PeriodProvider.getPeriodLength(ctx)
        val start = PeriodProvider.todayJulianDay()
        val t = Time(TimeZone.getDefault().id)
        val sb = StringBuilder()
        for (i in 0 until 30) {
            val jd = start + i
            t.setJulianDay(jd)
            t.normalize()
            val phase = PeriodEngine.phaseFor(anchor, cycle, length, jd)
            val label = when (phase) {
                PeriodEngine.PERIOD -> getString(R.string.sub_period_badge_period)
                PeriodEngine.PREDICTED_START -> getString(R.string.sub_period_badge_predicted)
                PeriodEngine.OVULATION -> getString(R.string.sub_period_badge_ovulation)
                PeriodEngine.FERTILE -> getString(R.string.sub_period_badge_fertile)
                else -> getString(R.string.sub_period_badge_none)
            }
            sb.append(if (i == 0) "\u25cf " else "  ")
                .append(String.format(Locale.US, "%02d-%02d  ", t.getMonth() + 1, t.getDay()))
                .append(label)
            if (i < 29) sb.append('\n')
        }
        return sb.toString()
    }
}
