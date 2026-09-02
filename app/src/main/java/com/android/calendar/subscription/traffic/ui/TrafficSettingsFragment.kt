/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.traffic.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.traffic.TrafficProvider
import com.android.calendar.subscription.traffic.data.TrafficRules
import ws.xsoh.etar.R
import java.util.Locale
import java.util.TimeZone

/**
 * Settings for the 限行 subscription: rule mode, the user's plate tail digit
 * (0-9 chips), the current weekday→digits rotation table with a "next
 * rotation" corrector, and a 14-day preview of the days the user may not
 * drive. Large screens split into two columns (layout-sw600dp).
 */
class TrafficSettingsFragment : Fragment() {

    private lateinit var status: TextView
    private lateinit var digitGrid: LinearLayout
    private lateinit var ruleTable: TextView
    private lateinit var preview: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_traffic_settings, container, false)
        status = root.findViewById(R.id.traffic_status)
        digitGrid = root.findViewById(R.id.traffic_digit_grid)
        ruleTable = root.findViewById(R.id.traffic_rule_table)
        preview = root.findViewById(R.id.traffic_preview)

        val ctx = requireContext()
        root.findViewById<View>(R.id.btn_mode_tail).setOnClickListener {
            TrafficProvider.setMode(ctx, TrafficRules.MODE_TAIL_NUMBER); refresh()
        }
        root.findViewById<View>(R.id.btn_mode_odd_even).setOnClickListener {
            TrafficProvider.setMode(ctx, TrafficRules.MODE_ODD_EVEN); refresh()
        }
        root.findViewById<View>(R.id.btn_rotate_group).setOnClickListener {
            val today = TrafficProvider.todayJulianDay()
            val next = (TrafficProvider.getGroupOffset(ctx, today) + 1) % TrafficRules.PAIRS.size
            TrafficProvider.setGroupOffset(ctx, next); refresh()
        }
        root.findViewById<View>(R.id.btn_disable).setOnClickListener {
            TrafficProvider.onDisabled(ctx); refresh()
        }

        buildDigitGrid(ctx)
        refresh()
        activity?.title = getString(R.string.sub_traffic_name)
        return root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sub_traffic_name)
    }

    private fun buildDigitGrid(ctx: Context) {
        digitGrid.removeAllViews()
        val inflater = LayoutInflater.from(ctx)
        var row: LinearLayout? = null
        for (d in 0..9) {
            if (d % 5 == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                digitGrid.addView(row)
            }
            val chip = inflater.inflate(R.layout.item_traffic_digit, row, false) as TextView
            chip.text = d.toString()
            (chip.layoutParams as LinearLayout.LayoutParams).setMargins(0, 8, 16, 0)
            chip.setOnClickListener {
                val current = TrafficProvider.getTailDigit(ctx)
                TrafficProvider.setTailDigit(ctx, if (current == d) -1 else d)
                refresh()
            }
            row!!.addView(chip)
        }
    }

    private fun refresh() {
        val ctx = requireContext()
        val summary = TrafficProvider.getCurrentSummary(ctx)
        status.text = summary ?: getString(R.string.sub_traffic_state_disabled)
        syncDigitSelection(ctx)
        ruleTable.text = buildRuleTable(ctx)
        preview.text = buildPreview(ctx)
    }

    private fun syncDigitSelection(ctx: Context) {
        val selected = TrafficProvider.getTailDigit(ctx)
        for (i in 0 until digitGrid.childCount) {
            val row = digitGrid.getChildAt(i) as LinearLayout
            for (j in 0 until row.childCount) {
                val chip = row.getChildAt(j) as TextView
                val digit = chip.text.toString().toIntOrNull() ?: continue
                val on = digit == selected
                chip.setBackgroundResource(
                    if (on) R.drawable.bg_traffic_digit_selected
                    else R.drawable.bg_traffic_digit)
                chip.setTextColor(
                    if (on) 0xFFD32F2F.toInt()
                    else resources.getColor(android.R.color.darker_gray, null))
            }
        }
    }

    private fun buildRuleTable(ctx: Context): String {
        if (TrafficProvider.getMode(ctx) == TrafficRules.MODE_ODD_EVEN) {
            return getString(R.string.sub_traffic_rule_odd_even_body)
        }
        val today = TrafficProvider.todayJulianDay()
        val offset = TrafficProvider.getGroupOffset(ctx, today)
        val names = weekdayNames()
        val sb = StringBuilder()
        for (i in 0..4) {
            val pair = TrafficRules.PAIRS[(i + offset) % TrafficRules.PAIRS.size]
            sb.append(names[i + 1]).append("  ")
                .append(getString(R.string.sub_traffic_badge_digits_fmt, pair[0], pair[1]))
            if (i < 4) sb.append('\n')
        }
        return sb.toString()
    }

    private fun buildPreview(ctx: Context): String {
        val tail = TrafficProvider.getTailDigit(ctx)
        if (tail < 0) return getString(R.string.sub_traffic_plate_hint)
        val mode = TrafficProvider.getMode(ctx)
        val start = TrafficProvider.todayJulianDay()
        val offset = TrafficProvider.getGroupOffset(ctx, start)
        val tz = TimeZone.getDefault()
        val t = Time(tz.id)
        val names = weekdayNames()
        val sb = StringBuilder()
        for (i in 0 until 14) {
            val jd = start + i
            t.setJulianDay(jd)
            t.normalize()
            val restricted = TrafficRules.isRestricted(mode, tail, offset, jd, t.getDay())
            sb.append(if (i == 0) "\u25cf " else "  ")
                .append(String.format(Locale.US, "%02d-%02d ", t.getMonth() + 1, t.getDay()))
                .append(names[TrafficRules.weekDayOf(jd)])
                .append("  ")
                .append(getString(
                    if (restricted) R.string.sub_traffic_preview_restricted
                    else R.string.sub_traffic_preview_free))
            if (i < 13) sb.append('\n')
        }
        return sb.toString()
    }

    /** index 0 = Sunday .. 6 = Saturday */
    private fun weekdayNames(): Array<String> = arrayOf(
        getString(R.string.sub_weekday_sun), getString(R.string.sub_weekday_mon),
        getString(R.string.sub_weekday_tue), getString(R.string.sub_weekday_wed),
        getString(R.string.sub_weekday_thu), getString(R.string.sub_weekday_fri),
        getString(R.string.sub_weekday_sat)
    )
}
