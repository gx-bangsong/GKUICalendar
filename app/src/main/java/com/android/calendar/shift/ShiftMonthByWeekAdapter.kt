package com.android.calendar.shift

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ListView
import com.android.calendar.month.MonthByWeekAdapter
import com.android.calendar.month.SimpleWeeksAdapter
import com.android.calendar.month.SimpleWeekView
import com.android.calendar.month.MonthWeekEventsView

class ShiftMonthByWeekAdapter(context: Context, params: java.util.HashMap<String, Int>, handler: Handler)
    : MonthByWeekAdapter(context, params, handler) {

    private val selectedDaysMap = mutableMapOf<Int, Int>()
    var onDayPaintedListener: ((Int) -> Unit)? = null

    var paintModeEnabled: Boolean = false
    private var lastPaintedJd: Int = -1

    fun setSelectedDays(days: Map<Int, Int>) {
        Log.e("ShiftDebug", "ADAPTER: Updating selection map with ${days.size} items")
        selectedDaysMap.clear()
        selectedDaysMap.putAll(days)
        notifyDataSetChanged()
    }

    private fun getLocalJd(time: com.android.calendar.calendarcommon2.Time): Int {
        return com.android.calendar.calendarcommon2.Time.getJulianDay(time.toMillis(), time.getGmtOffset())
    }

    private fun getListView(v: View): ListView? {
        var p = v.parent
        while (p != null) {
            if (p is ListView) return p
            p = p.parent
        }
        return null
    }

    override fun onDayTapped(day: com.android.calendar.calendarcommon2.Time) {
        val jd = getLocalJd(day)
        Log.e("ShiftDebug", "ADAPTER: onDayTapped JD=$jd, paintMode=$paintModeEnabled")
        if (!paintModeEnabled) {
            super.onDayTapped(day)
        } else {
            onDayPaintedListener?.invoke(jd)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (paintModeEnabled) {
            val action = event.action
            val listView = getListView(v)

            if (action == MotionEvent.ACTION_DOWN) {
                Log.e("ShiftDebug", "TOUCH: DOWN detected on ${v.hashCode()}")
                v.parent?.requestDisallowInterceptTouchEvent(true)
                listView?.requestDisallowInterceptTouchEvent(true)
            }

            if (listView != null) {
                val x = event.rawX
                val y = event.rawY

                val listLoc = IntArray(2)
                listView.getLocationOnScreen(listLoc)
                val relativeX = x - listLoc[0]
                val relativeY = y - listLoc[1]

                val position = listView.pointToPosition(relativeX.toInt(), relativeY.toInt())
                if (position != ListView.INVALID_POSITION) {
                    val child = listView.getChildAt(position - listView.firstVisiblePosition)
                    if (child is SimpleWeekView) {
                        val childLoc = IntArray(2)
                        child.getLocationOnScreen(childLoc)
                        val touchXInChild = x - childLoc[0]

                        val time = child.getDayFromLocation(touchXInChild)
                        if (time != null) {
                            val jd = getLocalJd(time)
                            if (jd != lastPaintedJd) {
                                Log.e("ShiftDebug", "TOUCH: Found JD=$jd, signaling listener")
                                lastPaintedJd = jd
                                try {
                                    child.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                } catch (e: Exception) {}
                                onDayPaintedListener?.invoke(jd)
                                listView.invalidateViews()
                            }
                        }
                    }
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    Log.e("ShiftDebug", "TOUCH: UP/CANCEL detected")
                    lastPaintedJd = -1
                }
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

        v.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
