/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.birthday.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.calendar.subscription.birthday.BirthdayProvider
import com.android.calendar.subscription.birthday.data.LunarBirthday
import ws.xsoh.etar.R

/**
 * Settings for 农历生日: a name field plus lunar month/day steppers to add an
 * entry, the current list with per-row remove buttons, and a disable button.
 */
class BirthdaySettingsFragment : Fragment() {

    private lateinit var status: TextView
    private lateinit var nameField: EditText
    private lateinit var dateValue: TextView
    private lateinit var monthValue: TextView
    private lateinit var dayValue: TextView
    private lateinit var list: LinearLayout

    private var month = 1
    private var day = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_birthday_settings, container, false)
        val ctx = requireContext()
        status = root.findViewById(R.id.birthday_status)
        nameField = root.findViewById(R.id.birthday_name)
        dateValue = root.findViewById(R.id.birthday_date_value)
        monthValue = root.findViewById(R.id.birthday_month_value)
        dayValue = root.findViewById(R.id.birthday_day_value)
        list = root.findViewById(R.id.birthday_list)

        root.findViewById<View>(R.id.btn_month_minus).setOnClickListener {
            month = wrap(month - 1, 1, 12); refresh()
        }
        root.findViewById<View>(R.id.btn_month_plus).setOnClickListener {
            month = wrap(month + 1, 1, 12); refresh()
        }
        root.findViewById<View>(R.id.btn_day_minus).setOnClickListener {
            day = wrap(day - 1, 1, 30); refresh()
        }
        root.findViewById<View>(R.id.btn_day_plus).setOnClickListener {
            day = wrap(day + 1, 1, 30); refresh()
        }
        root.findViewById<View>(R.id.btn_add_birthday).setOnClickListener { addEntry(ctx) }
        root.findViewById<View>(R.id.btn_disable).setOnClickListener {
            BirthdayProvider.onDisabled(ctx); refresh()
        }

        refresh()
        activity?.title = getString(R.string.sub_lunar_birthday_name)
        return root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sub_lunar_birthday_name)
    }

    private fun wrap(v: Int, min: Int, max: Int): Int =
        if (v < min) max else if (v > max) min else v

    private fun addEntry(ctx: Context) {
        val name = nameField.text.toString().trim()
        if (name.isEmpty()) {
            nameField.error = getString(R.string.sub_birthday_name_required)
            return
        }
        val items = ArrayList(BirthdayProvider.getBirthdays(ctx))
        items.add(LunarBirthday(name, month, day))
        BirthdayProvider.setBirthdays(ctx, items)
        nameField.setText("")
        refresh()
    }

    private fun refresh() {
        val ctx = requireContext()
        status.text = BirthdayProvider.getCurrentSummary(ctx)
            ?: getString(R.string.sub_birthday_state_disabled)
        monthValue.text = month.toString()
        dayValue.text = day.toString()
        dateValue.text = getString(R.string.sub_birthday_lunar_date_fmt, month, day)
        rebuildList(ctx)
    }

    private fun rebuildList(ctx: Context) {
        list.removeAllViews()
        val items = BirthdayProvider.getBirthdays(ctx)
        if (items.isEmpty()) {
            val tv = TextView(ctx)
            tv.text = getString(R.string.sub_birthday_empty)
            list.addView(tv)
            return
        }
        val inflater = LayoutInflater.from(ctx)
        for (i in items.indices) {
            val b = items[i]
            val row = inflater.inflate(R.layout.item_birthday, list, false)
            row.findViewById<TextView>(R.id.birthday_item_name).text = b.name
            row.findViewById<TextView>(R.id.birthday_item_date).text =
                getString(R.string.sub_birthday_lunar_date_fmt, b.lunarMonth, b.lunarDay)
            row.findViewById<View>(R.id.birthday_item_remove).setOnClickListener {
                val next = ArrayList(BirthdayProvider.getBirthdays(ctx))
                if (i < next.size) next.removeAt(i)
                BirthdayProvider.setBirthdays(ctx, next)
                refresh()
            }
            list.addView(row)
        }
    }
}
