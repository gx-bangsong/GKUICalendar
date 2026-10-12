/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import android.os.Bundle
import android.provider.CalendarContract.Attendees
import android.text.format.DateFormat as AndroidDateFormat
import android.text.format.DateUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.AllInOneActivity
import com.android.calendar.CalendarController
import com.android.calendar.CalendarController.EventType
import com.android.calendar.Event
import com.android.calendar.Utils
import com.android.calendar.month.MonthGridAdapter
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import ws.xsoh.etar.R

/**
 * The month entry in its 月历＋日程 layout: the month grid plus the schedule of
 * the selected day. Tapping a date only selects it and refreshes the list; it
 * never switches view or creates an event. The browsing month is shown in the
 * toolbar, so this fragment has no header of its own.
 *
 * The selected date is mirrored into the CalendarController time so that the
 * floating action button and other views use it.
 */
class MonthAgendaFragment : Fragment(), CalendarController.EventHandler {

    private lateinit var controller: CalendarController
    private lateinit var loader: MonthAgendaLoader
    private lateinit var zone: TimeZone
    private var firstDayOfWeek = Calendar.SUNDAY

    // Displayed month is 0-based; selection is a Julian day in the home time zone.
    private var displayYear = 0
    private var displayMonth = 0
    private var selectedJulian = 0
    private var rowCount = 6
    private var monthFirstCell = 0
    private var monthData: MonthAgendaLoader.MonthData? = null
    private var cellHeightPx = 0
    private var pendingListPosition = -1
    private var lastWidth = -1
    private var lastHeight = -1
    private var dayTitleFormat: SimpleDateFormat? = null
    private var cellDateFormat: SimpleDateFormat? = null

    private lateinit var body: LinearLayout
    private lateinit var gridColumn: LinearLayout
    private lateinit var dayColumn: LinearLayout
    private lateinit var gridClip: View
    private lateinit var dayTitle: TextView
    private lateinit var weekdayRow: LinearLayout
    private lateinit var gridView: RecyclerView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var gridAdapter: MonthGridAdapter
    private lateinit var listAdapter: MonthAgendaAdapter
    private lateinit var swipeDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        controller = CalendarController.getInstance(context)
        zone = TimeZone.getTimeZone(Utils.getTimeZone(context, null))
        firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(context)
        loader = MonthAgendaLoader(context)

        val args = requireArguments()
        val state = args.getBundle(ARG_STATE)
        val anchorJulian = julianOf(args.getLong(ARG_TIME, System.currentTimeMillis()))
        selectedJulian = if (state != null && state.containsKey(KEY_SELECTED)) {
            state.getInt(KEY_SELECTED)
        } else {
            anchorJulian
        }
        val selectedYmd = MonthGridMath.ymd(selectedJulian)
        val displayIndex = if (state != null && state.containsKey(KEY_DISPLAY_YM)) {
            state.getInt(KEY_DISPLAY_YM)
        } else {
            selectedYmd[0] * 12 + selectedYmd[1] - 1
        }
        displayYear = displayIndex / 12
        displayMonth = displayIndex % 12
        pendingListPosition = state?.getInt(KEY_LIST_POSITION, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_month_agenda, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        body = view.findViewById(R.id.agenda_body)
        gridColumn = view.findViewById(R.id.agenda_grid_column)
        dayColumn = view.findViewById(R.id.agenda_day_column)
        gridClip = view.findViewById(R.id.agenda_grid_clip)
        dayTitle = view.findViewById(R.id.agenda_day_title)
        weekdayRow = view.findViewById(R.id.agenda_weekdays)
        gridView = view.findViewById(R.id.agenda_grid)
        listView = view.findViewById(R.id.agenda_list)
        emptyView = view.findViewById(R.id.agenda_empty)

        gridAdapter = MonthGridAdapter { julian -> applySelection(julian, true) }
        gridView.layoutManager = GridLayoutManager(requireContext(), 7)
        gridView.adapter = gridAdapter
        gridView.itemAnimator = null
        gridView.isNestedScrollingEnabled = false

        // Horizontal fling on the grid flips the month. Taps still reach the cells because
        // this listener never consumes the touch.
        swipeDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dx) < dp(SWIPE_MIN_DP) || Math.abs(dx) < Math.abs(dy) * 1.5f) return false
                shiftMonth(if (dx < 0) 1 else -1)
                return true
            }
        })
        gridView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                swipeDetector.onTouchEvent(e)
                return false
            }
        })

        listAdapter = MonthAgendaAdapter(requireContext(), zone.id) { event -> openEvent(event) }
        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = listAdapter

        buildWeekdayHeader()
        body.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width != lastWidth || height != lastHeight) {
                applyAdaptiveLayout(width, height)
            }
        }
        refreshMonth(reload = true)
        loadDay()
    }

    override fun onDestroy() {
        if (::loader.isInitialized) loader.shutdown()
        super.onDestroy()
    }

    override fun getSupportedEventTypes(): Long = EventType.GO_TO or EventType.EVENTS_CHANGED

    override fun handleEvent(event: CalendarController.EventInfo) {
        if (event.eventType != EventType.GO_TO || !isViewReady()) return
        val today = (event.extraLong and CalendarController.EXTRA_GOTO_TODAY) != 0L
        val julian = if (today) {
            julianOf(System.currentTimeMillis())
        } else {
            val time = event.selectedTime ?: event.startTime ?: return
            julianOf(time.toMillis())
        }
        // "今天" and other go-to requests move the browsing month, the selection and the list
        // together. setTime does not dispatch an event, so the activity is not re-entered.
        applySelection(julian, false)
        sendSelectionToController()
    }

    override fun eventsChanged() {
        if (!isViewReady()) return
        refreshMonth(reload = true)
        loadDay()
    }

    /** Snapshot used by AllInOneActivity to restore this view after recreation or a layout switch. */
    fun saveState(): Bundle {
        val bundle = Bundle()
        bundle.putInt(KEY_SELECTED, selectedJulian)
        bundle.putInt(KEY_DISPLAY_YM, displayYear * 12 + displayMonth)
        val position = if (pendingListPosition >= 0) {
            pendingListPosition
        } else if (::listView.isInitialized) {
            (listView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        } else {
            0
        }
        bundle.putInt(KEY_LIST_POSITION, maxOf(position, 0))
        return bundle
    }

    private fun isViewReady(): Boolean = isAdded && view != null && ::gridView.isInitialized

    /** Selects [julian]; moves the displayed month when needed. */
    private fun applySelection(julian: Int, notifyController: Boolean) {
        if (!isViewReady()) return
        val ymd = MonthGridMath.ymd(julian)
        val sameMonth = ymd[0] == displayYear && ymd[1] == displayMonth + 1
        if (julian == selectedJulian && sameMonth) return
        selectedJulian = julian
        if (sameMonth) {
            renderGrid()
        } else {
            displayYear = ymd[0]
            displayMonth = ymd[1] - 1
            refreshMonth(reload = true)
        }
        loadDay()
        if (notifyController) sendSelectionToController()
    }

    private fun shiftMonth(delta: Int) {
        if (!isViewReady()) return
        val index = displayYear * 12 + displayMonth + delta
        displayYear = index / 12
        displayMonth = index % 12
        // Keep the same day-of-month when it exists in the new month, else the last day.
        val maxDay = MonthGridMath.daysInMonth(displayYear, displayMonth)
        val day = minOf(MonthGridMath.ymd(selectedJulian)[2], maxDay)
        selectedJulian = MonthGridMath.julianDay(displayYear, displayMonth + 1, day)
        refreshMonth(reload = true)
        loadDay()
        sendSelectionToController()
    }

    private fun refreshMonth(reload: Boolean) {
        if (!isViewReady()) return
        if (reload) monthData = null
        renderGrid()
        if (reload) loadMonth()
        notifyToolbarMonth()
    }

    /** Lets the toolbar show the browsing month. The toolbar is the only place for the month title. */
    private fun notifyToolbarMonth() {
        val millis = GregorianCalendar(zone).apply {
            clear()
            set(displayYear, displayMonth, 1)
        }.timeInMillis
        (activity as? AllInOneActivity)?.onMonthAgendaTitleChanged(millis)
    }

    private fun loadMonth() {
        prepareMonth()
        val first = monthFirstCell
        loader.loadMonth(first, rowCount * 7) { data ->
            if (isViewReady() && data.firstJulian == monthFirstCell) {
                monthData = data
                renderGrid()
            }
        }
    }

    private fun loadDay() {
        if (!isViewReady()) return
        val julian = selectedJulian
        dayTitle.text = dayTitleText(julian)
        listAdapter.submit(emptyList())
        emptyView.visibility = View.GONE
        loader.loadDay(julian) { events ->
            if (isViewReady() && julian == selectedJulian) {
                listAdapter.submit(events)
                emptyView.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                if (pendingListPosition >= 0 && events.isNotEmpty()) {
                    listView.scrollToPosition(minOf(pendingListPosition, events.size - 1))
                }
                pendingListPosition = -1
            }
        }
    }

    private fun prepareMonth() {
        rowCount = MonthGridMath.rowCount(displayYear, displayMonth, firstDayOfWeek)
        monthFirstCell = MonthGridMath.julianDay(displayYear, displayMonth + 1, 1) -
            MonthGridMath.leadingBlanks(displayYear, displayMonth, firstDayOfWeek)
    }

    private fun renderGrid() {
        if (cellHeightPx <= 0) return
        prepareMonth()
        val data = monthData?.takeIf { it.firstJulian == monthFirstCell }
        val todayJulian = julianOf(System.currentTimeMillis())
        val cells = ArrayList<MonthGridAdapter.Cell>(rowCount * 7)
        for (i in 0 until rowCount * 7) {
            val julian = monthFirstCell + i
            val ymd = MonthGridMath.ymd(julian)
            val inMonth = ymd[0] == displayYear && ymd[1] == displayMonth + 1
            val count = data?.dayCounts?.get(julian) ?: 0
            cells.add(
                MonthGridAdapter.Cell(
                    julianDay = julian,
                    dayOfMonth = ymd[2],
                    currentMonth = inMonth,
                    today = julian == todayJulian,
                    selected = julian == selectedJulian,
                    lunarText = data?.lunarText?.get(julian),
                    eventColors = data?.dayColors?.get(julian).orEmpty(),
                    description = describeDay(ymd, count)
                )
            )
        }
        val gridHeight = rowCount * cellHeightPx
        gridView.layoutParams = gridView.layoutParams.apply { height = gridHeight }
        gridClip.layoutParams = gridClip.layoutParams.apply { height = gridHeight }
        gridAdapter.submitCells(cells, cellHeightPx)
    }

    private fun describeDay(ymd: IntArray, count: Int): String {
        val locale = Locale.getDefault()
        val format = cellDateFormat ?: SimpleDateFormat(
            AndroidDateFormat.getBestDateTimePattern(locale, "yMMMMEEEEd"), locale
        ).apply { timeZone = zone }.also { cellDateFormat = it }
        val date = GregorianCalendar(zone).apply { clear(); set(ymd[0], ymd[1] - 1, ymd[2]) }.time
        val base = format.format(date)
        return if (count > 0) {
            base + ", " + resources.getQuantityString(R.plurals.agenda_events_count, count, count)
        } else {
            base
        }
    }

    /** Title of the schedule area; states which day the list below belongs to. */
    private fun dayTitleText(julian: Int): String {
        val locale = Locale.getDefault()
        val format = dayTitleFormat ?: SimpleDateFormat(
            AndroidDateFormat.getBestDateTimePattern(locale, "yMMMMEEEEd"), locale
        ).apply { timeZone = zone }.also { dayTitleFormat = it }
        val ymd = MonthGridMath.ymd(julian)
        val date = GregorianCalendar(zone).apply { clear(); set(ymd[0], ymd[1] - 1, ymd[2]) }.time
        return format.format(date)
    }

    private fun buildWeekdayHeader() {
        weekdayRow.removeAllViews()
        val color = MaterialColors.getColor(
            weekdayRow, com.google.android.material.R.attr.colorOnSurfaceVariant, 0
        )
        for (i in 0 until 7) {
            val dayOfWeek = ((firstDayOfWeek - 1 + i) % 7) + 1
            val label = TextView(requireContext()).apply {
                gravity = Gravity.CENTER
                text = DateUtils.getDayOfWeekString(dayOfWeek, DateUtils.LENGTH_SHORTEST)
                textSize = 12f
                if (color != 0) setTextColor(color)
            }
            weekdayRow.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /**
     * Chooses wide (side-by-side) or narrow (stacked) layout from the window size, not
     * from the device or orientation. The grid takes the top part of the narrow layout;
     * the schedule takes the rest.
     */
    private fun applyAdaptiveLayout(width: Int, height: Int) {
        if (!isViewReady() || body.width <= 0 || body.height <= 0) return
        lastWidth = width
        lastHeight = height
        val density = resources.displayMetrics.density
        val wide = width / density >= WIDE_MIN_WIDTH_DP && width > height
        val bodyWidth = body.width
        val bodyHeight = body.height
        val minCell = dp(MIN_CELL_DP)
        val maxCell = dp(MAX_CELL_DP)
        val matchParent = LinearLayout.LayoutParams.MATCH_PARENT

        if (wide) {
            body.orientation = LinearLayout.HORIZONTAL
            body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).apply {
                width = matchParent
                height = matchParent
            }
            gridColumn.layoutParams = LinearLayout.LayoutParams(
                minOf(bodyWidth / 2, dp(MAX_GRID_WIDTH_DP)), LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dayColumn.layoutParams = LinearLayout.LayoutParams(0, matchParent, 1f)
            cellHeightPx = ((bodyHeight - dp(WEEKDAY_HEIGHT_DP)) / 6).coerceIn(minCell, maxCell)
        } else {
            body.orientation = LinearLayout.VERTICAL
            body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).apply {
                width = matchParent
                height = matchParent
            }
            gridColumn.layoutParams = LinearLayout.LayoutParams(
                matchParent, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dayColumn.layoutParams = LinearLayout.LayoutParams(matchParent, 0, 1f)
            cellHeightPx = ((bodyHeight * 0.5f - dp(WEEKDAY_HEIGHT_DP)) / 6).toInt()
                .coerceIn(minCell, maxCell)
        }
        renderGrid()
    }

    /** Opens the event for the instance that was tapped, with its own begin and end. */
    private fun openEvent(event: Event) {
        val extra = CalendarController.EventInfo.buildViewExtraLong(
            Attendees.ATTENDEE_STATUS_NONE, event.allDay
        )
        controller.sendEventRelatedEventWithExtra(
            this, EventType.VIEW_EVENT, event.id, event.startMillis, event.endMillis,
            0, 0, extra, selectedDayMillis()
        )
    }

    /**
     * Mirrors the selected day into the controller time so that the floating action
     * button prefills it and switching views keeps it. setTime does not dispatch an
     * event, so the activity's view-switching and animations are not triggered.
     */
    private fun sendSelectionToController() {
        controller.setTime(selectedDayMillis())
    }

    /** Selected day at the controller's current time of day, in the home time zone. */
    private fun selectedDayMillis(): Long {
        val current = GregorianCalendar(zone).apply { timeInMillis = controller.time }
        val ymd = MonthGridMath.ymd(selectedJulian)
        return GregorianCalendar(zone).apply {
            clear()
            set(
                ymd[0], ymd[1] - 1, ymd[2],
                current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), 0
            )
        }.timeInMillis
    }

    private fun julianOf(millis: Long): Int {
        val cal = GregorianCalendar(zone).apply { timeInMillis = millis }
        return MonthGridMath.julianDay(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        const val ARG_TIME = "month_agenda_time"
        const val ARG_STATE = "month_agenda_state"
        private const val KEY_SELECTED = "month_agenda_selected"
        private const val KEY_DISPLAY_YM = "month_agenda_display_ym"
        private const val KEY_LIST_POSITION = "month_agenda_list_position"

        private const val WIDE_MIN_WIDTH_DP = 600f
        private const val MIN_CELL_DP = 44
        private const val MAX_CELL_DP = 64
        private const val WEEKDAY_HEIGHT_DP = 28
        private const val MAX_GRID_WIDTH_DP = 560
        private const val SWIPE_MIN_DP = 64

        @JvmStatic
        fun newInstance(timeMillis: Long, state: Bundle?): MonthAgendaFragment =
            MonthAgendaFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TIME, timeMillis)
                    if (state != null) putBundle(ARG_STATE, state)
                }
            }
    }
}
