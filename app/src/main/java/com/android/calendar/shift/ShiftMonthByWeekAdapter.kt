package com.android.calendar.shift

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import com.android.calendar.month.MonthByWeekAdapter
import com.android.calendar.month.SimpleWeeksAdapter
import com.android.calendar.month.SimpleWeekView
import com.android.calendar.month.MonthWeekEventsView

class ShiftMonthByWeekAdapter(context: Context, params: java.util.HashMap<String, Int>, handler: Handler)
    : MonthByWeekAdapter(context, params, handler) {

    private val selectedDaysMap = mutableMapOf<Int, Int>() // Local JulianDay -> Color
    var onDayPaintedListener: ((Int) -> Unit)? = null

    var paintModeEnabled: Boolean = false
    private var lastPaintedJd: Int = -1

    fun setSelectedDays(days: Map<Int, Int>) {
        Log.d("ShiftDebug", "setSelectedDays: adapter received ${days.size} days")
        selectedDaysMap.clear()
        selectedDaysMap.putAll(days)
        notifyDataSetChanged()
    }

    private fun getLocalJd(time: com.android.calendar.calendarcommon2.Time): Int {
        return com.android.calendar.calendarcommon2.Time.getJulianDay(time.toMillis(), time.getGmtOffset())
    }

    override fun onDayTapped(day: com.android.calendar.calendarcommon2.Time) {
        val jd = getLocalJd(day)
        Log.d("ShiftDebug", "onDayTapped: JD=$jd, paintMode=$paintModeEnabled")
        if (!paintModeEnabled) {
            super.onDayTapped(day)
        } else {
            onDayPaintedListener?.invoke(jd)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (paintModeEnabled && v is ShiftMonthWeekView) {
            val action = event.action
            if (action == MotionEvent.ACTION_DOWN) {
                v.parent.requestDisallowInterceptTouchEvent(true)
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                val time = v.getDayFromLocation(event.x)
                if (time != null) {
                    val jd = getLocalJd(time)
                    if (jd != lastPaintedJd) {
                        Log.d("ShiftDebug", "onDayPainted (Touch): JD=$jd")
                        lastPaintedJd = jd
                        onDayPaintedListener?.invoke(jd)
                    }
                }
                return true
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                lastPaintedJd = -1
                return true
            }
        }
        return super.onTouch(v, event)
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
