package com.android.calendar.shift

import android.content.Context
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.month.MonthByWeekAdapter
import java.util.HashMap
import java.util.HashSet

class ShiftMonthByWeekAdapter(
    context: Context,
    params: HashMap<String, Int>,
    handler: Handler?
) : MonthByWeekAdapter(context, params, handler) {

    private val selectedDays = HashSet<Int>()
    private var selectionColor: Int = 0
    var onDayTappedListener: ((Int) -> Unit)? = null

    fun setSelectedDays(days: Set<Int>, color: Int) {
        selectedDays.clear()
        selectedDays.addAll(days)
        selectionColor = color
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v: ShiftMonthWeekView
        if (convertView != null && convertView is ShiftMonthWeekView) {
            v = convertView
        } else {
            v = ShiftMonthWeekView(mContext)
        }

        val view = super.getView(position, v, parent) as ShiftMonthWeekView
        view.setSelectedDays(selectedDays, selectionColor)
        return view
    }

    override fun onDayTapped(day: Time) {
        onDayTappedListener?.invoke(Time.getJulianDay(day.toMillis(), 0))
    }
}
