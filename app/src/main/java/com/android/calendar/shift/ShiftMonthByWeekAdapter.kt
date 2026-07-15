package com.android.calendar.shift

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import com.android.calendar.month.MonthByWeekAdapter
import com.android.calendar.month.SimpleWeeksAdapter
import com.android.calendar.month.SimpleWeekView
import com.android.calendar.month.MonthWeekEventsView

class ShiftMonthByWeekAdapter(context: Context, params: java.util.HashMap<String, Int>, handler: Handler)
    : MonthByWeekAdapter(context, params, handler) {

    private val selectedDaysMap = mutableMapOf<Int, Int>()
    var paintModeEnabled: Boolean = false

    fun setSelectedDays(days: Map<Int, Int>) {
        selectedDaysMap.clear()
        selectedDaysMap.putAll(days)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = if (convertView is ShiftMonthWeekView) {
            convertView
        } else {
            ShiftMonthWeekView(mContext)
        }

        v.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val drawingParams = java.util.HashMap<String, Int>()
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_HEIGHT, parent.height / mNumWeeks)
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK_START, mFirstDayOfWeek)
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SHOW_WK_NUM, if (mShowWeekNumber) 1 else 0)
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_NUM_DAYS, mDaysPerWeek)
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK, position)
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SELECTED_DAY, -1)
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_FOCUS_MONTH, mFocusMonth)
        drawingParams.put(MonthWeekEventsView.VIEW_PARAMS_ORIENTATION, mOrientation)

        v.setWeekParams(drawingParams, mSelectedDay.timezone)
        v.setSelection(selectedDaysMap)
        return v
    }
}
