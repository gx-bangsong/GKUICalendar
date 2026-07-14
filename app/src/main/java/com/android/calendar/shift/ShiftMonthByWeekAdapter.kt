package com.android.calendar.shift

import android.content.Context
import android.graphics.Rect
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
        Log.e("ShiftDebug", "ADAPTER: Updating UI with ${days.size} shifts")
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
        if (!paintModeEnabled) {
            super.onDayTapped(day)
        } else {
            val jd = getLocalJd(day)
            Log.e("ShiftDebug", "ADAPTER: Tap detected JD=$jd")
            onDayPaintedListener?.invoke(jd)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (paintModeEnabled) {
            val action = event.action
            val listView = getListView(v) ?: return true

            // Strictly prevent ListView from scrolling
            listView.requestDisallowInterceptTouchEvent(true)

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                lastPaintedJd = -1
                return true
            }

            val x = event.rawX
            val y = event.rawY

            // Precision scan: which row and which day is exactly under the finger?
            for (i in 0 until listView.childCount) {
                val child = listView.getChildAt(i)
                val rect = Rect()
                child.getGlobalVisibleRect(rect)
                if (rect.contains(x.toInt(), y.toInt())) {
                    if (child is SimpleWeekView) {
                        val touchXInChild = x - rect.left
                        val time = child.getDayFromLocation(touchXInChild)
                        if (time != null) {
                            val jd = getLocalJd(time)
                            if (jd != lastPaintedJd) {
                                Log.e("ShiftDebug", "PAINT HIT! JD=$jd in row $i")
                                lastPaintedJd = jd
                                // Trigger vibration
                                child.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onDayPaintedListener?.invoke(jd)
                            }
                        }
                    }
                    break
                }
            }
            return true
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
        // Pass paint mode to view for background tinting
        v.paintModeEnabled = paintModeEnabled

        return v
    }
}
