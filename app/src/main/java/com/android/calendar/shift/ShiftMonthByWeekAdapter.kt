package com.android.calendar.shift

import android.content.Context
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import com.android.calendar.month.MonthByWeekAdapter
import com.android.calendar.month.SimpleWeeksAdapter

class ShiftMonthByWeekAdapter(context: Context, params: java.util.HashMap<String, Int>, handler: Handler)
    : MonthByWeekAdapter(context, params, handler) {

    private val selectedDaysMap = mutableMapOf<Int, Int>() // JulianDay -> Color
    var onDayTappedListener: ((Int) -> Unit)? = null

    fun setSelectedDays(days: Map<Int, Int>) {
        selectedDaysMap.clear()
        selectedDaysMap.putAll(days)
        notifyDataSetChanged()
    }

    override fun onDayTapped(day: com.android.calendar.calendarcommon2.Time) {
        onDayTappedListener?.invoke(com.android.calendar.calendarcommon2.Time.getJulianDay(day.toMillis(), 0))
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = if (convertView is ShiftMonthWeekView) {
            convertView
        } else {
            ShiftMonthWeekView(mContext)
        }

        val params = AbsListView.LayoutParams(
            AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.MATCH_PARENT
        )
        v.layoutParams = params
        v.setClickable(true)
        v.setOnTouchListener(this)

        val drawingParams = java.util.HashMap<String, Int>()
        // SimpleWeekView constants
        drawingParams.put("height", parent.height / mNumWeeks)
        drawingParams.put(SimpleWeeksAdapter.WEEK_PARAMS_WEEK_START, mFirstDayOfWeek)
        drawingParams.put(SimpleWeeksAdapter.WEEK_PARAMS_JULIAN_DAY, mFirstJulianDay + position * mDaysPerWeek)
        drawingParams.put(SimpleWeeksAdapter.WEEK_PARAMS_SHOW_WEEK, if (mShowWeekNumber) 1 else 0)
        drawingParams.put(SimpleWeeksAdapter.WEEK_PARAMS_DAYS_PER_WEEK, mDaysPerWeek)
        drawingParams.put("week", position)
        drawingParams.put(SimpleWeeksAdapter.WEEK_PARAMS_FOCUS_MONTH, mFocusMonth)

        v.setWeekParams(drawingParams, mSelectedDay.timezone)
        v.setSelection(selectedDaysMap)
        v.invalidate()

        return v
    }
}
