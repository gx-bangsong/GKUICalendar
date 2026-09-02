/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.shift.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.subscription.shift.ShiftProvider
import com.android.calendar.subscription.shift.data.ShiftEngine
import com.android.calendar.subscription.shift.data.ShiftPresets
import com.android.calendar.subscription.shift.data.ShiftType
import ws.xsoh.etar.R
import java.util.Locale
import java.util.TimeZone

/**
 * Xiaomi-style shift settings screen: five quick-fill preset buttons, a
 * cycle-length summary row, an anchor-date row (tap resets anchor to
 * today), a per-day toggle grid where tapping a day cycles through
 * 早→中→晚→休, a disable button, and a 14-day monospace preview.
 *
 * All edits are persisted immediately to SharedPreferences so the back
 * arrow and screen-rotation don't lose changes. Large screens
 * (layout-sw600dp) split into two columns: editor on the left, preview
 * on the right.
 */
class ShiftSettingsFragment : Fragment() {

    private lateinit var status: TextView
    private lateinit var cycleSummary: TextView
    private lateinit var anchorSummary: TextView
    private lateinit var anchorRow: View
    private lateinit var dayGrid: LinearLayout
    private lateinit var preview: TextView

    private var cycle: IntArray = ShiftPresets.cycleForKey(ShiftPresets.KEY_THREE)!!
    private var anchorJd: Int = todayJulianDay()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_shift_settings, container, false)
        status = root.findViewById(R.id.shift_status)
        cycleSummary = root.findViewById(R.id.shift_cycle_summary)
        anchorSummary = root.findViewById(R.id.shift_anchor_summary)
        anchorRow = root.findViewById(R.id.shift_anchor_row)
        dayGrid = root.findViewById(R.id.shift_day_grid)
        preview = root.findViewById(R.id.shift_preview)

        root.findViewById<View>(R.id.btn_preset_three)
            .setOnClickListener { applyPreset(ShiftPresets.KEY_THREE) }
        root.findViewById<View>(R.id.btn_preset_fourthree)
            .setOnClickListener { applyPreset(ShiftPresets.KEY_FOUR_THREE) }
        root.findViewById<View>(R.id.btn_preset_one_one)
            .setOnClickListener { applyPreset(ShiftPresets.KEY_ONE_ON_ONE_OFF) }
        root.findViewById<View>(R.id.btn_preset_two_two)
            .setOnClickListener { applyPreset(ShiftPresets.KEY_TWO_ON_TWO_OFF) }
        root.findViewById<View>(R.id.btn_preset_day)
            .setOnClickListener { applyPreset(ShiftPresets.KEY_DAY_SHIFT) }
        root.findViewById<View>(R.id.btn_disable).setOnClickListener {
            ShiftProvider.onDisabled(requireContext()); refresh() }
        anchorRow.setOnClickListener { anchorJd = todayJulianDay(); persist(); refresh() }

        loadState()
        refresh()
        activity?.title = getString(R.string.sub_shift_name)
        return root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sub_shift_name)
    }

    private fun loadState() {
        val state = ShiftProvider.loadState(requireContext())
        if (state != null) {
            cycle = state.first.copyOf()
            anchorJd = state.second
        }
    }

    private fun applyPreset(key: String) {
        val c = ShiftPresets.cycleForKey(key) ?: return
        cycle = c.copyOf()
        anchorJd = todayJulianDay()
        persist(); refresh()
    }

    private fun persist() {
        ShiftProvider.saveCustomCycle(requireContext(), cycle, anchorJd)
    }

    private fun refresh() {
        val ctx = requireContext()
        val sum = ShiftProvider.getCurrentSummary(ctx)
        if (sum == null) {
            status.text = getString(R.string.sub_shift_state_disabled)
            cycleSummary.text = ""
            anchorSummary.text = ""
            preview.text = ""
            dayGrid.removeAllViews()
            return
        }
        status.text = sum
        cycleSummary.text = getString(R.string.sub_shift_cycle_days_fmt, cycle.size)
        anchorSummary.text = formatAnchorDate(ctx, anchorJd)
        rebuildDayGrid(ctx)
        preview.text = buildPreview(ctx)
    }

    private fun rebuildDayGrid(ctx: Context) {
        dayGrid.removeAllViews()
        val inflater = LayoutInflater.from(ctx)
        val columns = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        var row: LinearLayout? = null
        for (i in cycle.indices) {
            if (i % columns == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                dayGrid.addView(row)
            }
            val item = inflater.inflate(R.layout.item_shift_day, row, false)
            val label: TextView = item.findViewById(R.id.day_label)
            val value: TextView = item.findViewById(R.id.day_value)
            label.text = getString(R.string.sub_shift_day_n_fmt, i + 1)
            bindDayChip(value, cycle[i])
            val lp = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            item.layoutParams = lp
            value.setOnClickListener {
                cycle[i] = nextType(cycle[i])
                bindDayChip(value, cycle[i])
                persist()
                // update status+preview only (grid stays)
                status.text = ShiftProvider.getCurrentSummary(ctx)
                cycleSummary.text = getString(R.string.sub_shift_cycle_days_fmt, cycle.size)
                preview.text = buildPreview(ctx)
            }
            row!!.addView(item)
        }
    }

    private fun bindDayChip(v: TextView, type: Int) {
        val labelRes = when (type) {
            ShiftType.MORNING   -> R.string.sub_shift_time_morning
            ShiftType.AFTERNOON -> R.string.sub_shift_time_afternoon
            ShiftType.NIGHT     -> R.string.sub_shift_time_night
            else                -> R.string.sub_shift_rest
        }
        val chipBg = when (type) {
            ShiftType.MORNING   -> R.drawable.bg_shift_chip_morning
            ShiftType.AFTERNOON -> R.drawable.bg_shift_chip_afternoon
            ShiftType.NIGHT     -> R.drawable.bg_shift_chip_night
            else                -> R.drawable.bg_shift_chip_rest
        }
        val textColor = ShiftPresets.badgeColor(type)
        v.text = getString(
            if (type == ShiftType.REST) R.string.sub_shift_rest else R.string.sub_shift_day_n_fmt,
            if (type == ShiftType.REST) 0 else labelRes)
        // Set the label to "早班/中班/晚班/休息" directly.
        v.text = when (type) {
            ShiftType.MORNING   -> "\u65e9\u73ed " + getString(R.string.sub_shift_time_morning)
            ShiftType.AFTERNOON -> "\u4e2d\u73ed " + getString(R.string.sub_shift_time_afternoon)
            ShiftType.NIGHT     -> "\u665a\u73ed " + getString(R.string.sub_shift_time_night)
            else                -> getString(R.string.sub_shift_rest)
        }
        v.setBackgroundResource(chipBg)
        v.setTextColor(textColor)
    }

    private fun nextType(t: Int): Int = when (t) {
        ShiftType.MORNING   -> ShiftType.AFTERNOON
        ShiftType.AFTERNOON -> ShiftType.NIGHT
        ShiftType.NIGHT     -> ShiftType.REST
        else                -> ShiftType.MORNING
    }

    private fun formatAnchorDate(ctx: Context, jd: Int): String {
        val tz = TimeZone.getDefault()
        val t = Time(tz.id)
        t.setJulianDay(jd); t.normalize()
        val weekdayNames = arrayOf("\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c",
            "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d")
        val wd = t.weekDay.coerceIn(0, 6)
        return getString(R.string.sub_shift_anchor_date_fmt,
            t.getYear(), t.getMonth() + 1, t.getDay()) +
            " " + weekdayNames[wd]
    }

    private fun buildPreview(ctx: Context): String {
        val jd0 = anchorJd
        val tz = TimeZone.getDefault()
        val todayJd = Time.getJulianDay(System.currentTimeMillis(),
            tz.getOffset(System.currentTimeMillis()) / 1000L)
        val startJd = todayJd.coerceAtLeast(jd0)
        val t = Time(tz.id)
        val sb = StringBuilder()
        val dayNames = arrayOf("\u65e5", "\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d")
        for (i in 0 until 14) {
            val jd = startJd + i
            val type = ShiftEngine.typeFor(cycle, jd0, jd)
            val label = if (type < 0) "-" else ShiftEngine.labelFor(type)
            t.setJulianDay(jd); t.normalize()
            val wd = t.getWeekDay().coerceIn(0, 6)
            val marker = if (jd == todayJd) "\u25cf " else "  "
            sb.append(String.format(Locale.getDefault(),
                "%s%d/%d \u5468%s  %s\n",
                marker, t.getMonth() + 1, t.getDay(), dayNames[wd], label))
        }
        return sb.toString().trimEnd()
    }

    private fun todayJulianDay(): Int {
        val tz = TimeZone.getDefault()
        return Time.getJulianDay(System.currentTimeMillis(),
            tz.getOffset(System.currentTimeMillis()) / 1000L)
    }
}
