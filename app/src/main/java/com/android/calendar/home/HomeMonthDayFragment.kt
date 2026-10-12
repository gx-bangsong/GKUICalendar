/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

import android.os.Bundle
import android.provider.CalendarContract.Attendees
import android.text.format.DateFormat as AndroidDateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.CalendarController
import com.android.calendar.CalendarController.EventType
import com.android.calendar.Event
import com.android.calendar.R
import com.android.calendar.Utils
import com.android.calendar.month.MonthGridAdapter
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Month grid plus the selected day's schedule. Tapping a date only selects it and
 * refreshes the list; it never switches to a day view or creates an event.
 * The selected date is mirrored into the CalendarController time so that the
 * floating action button and other views use it.
 */
class HomeMonthDayFragment : Fragment(), CalendarController.EventHandler {

    private lateinit var controller: CalendarController
    private lateinit var loader: HomeDataLoader
    private lateinit var zone: TimeZone
    private var firstDayOfWeek = Calendar.SUNDAY

    // Displayed month is 0-based; selection is a Julian day in the home time zone.
    private var displayYear = 0
    private var displayMonth = 0
    private var selectedJulian = 0
    private var rowCount = 6
    private var monthFirstCell = 0
    private var monthData: HomeDataLoader.MonthData? = null
    private var cellHeightPx = 0
    private var gridCollapsed = false
    private var userSetCollapse = false
    private var pendingListPosition = -1
    private var lastWidth = -1
    private var lastHeight = -1
    private var monthTitleFormat: SimpleDateFormat? = null
    private var cellDateFormat: SimpleDateFormat? = null

    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var gridContainer: LinearLayout
    private lateinit var listFrame: View
    private lateinit var titleView: TextView
    private lateinit var toggleButton: ImageButton
    private lateinit var weekdayRow: LinearLayout
    private lateinit var gridView: RecyclerView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var gridAdapter: MonthGridAdapter
    private lateinit var listAdapter: HomeEventsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        controller = CalendarController.getInstance(context)
        zone = TimeZone.getTimeZone(Utils.getTimeZone(context, null))
        firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(context)
        loader = HomeDataLoader(context)

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
        gridCollapsed = state?.getBoolean(KEY_COLLAPSED, false) ?: false
        userSetCollapse = state?.getBoolean(KEY_USER_SET_COLLAPSE, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home_month_day, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view.findViewById(R.id.home_root)
        body = view.findViewById(R.id.home_body)
        gridContainer = view.findViewById(R.id.home_grid_container)
        listFrame = view.findViewById(R.id.home_list_frame)
        titleView = view.findViewById(R.id.home_title)
        toggleButton = view.findViewById(R.id.home_toggle)
        weekdayRow = view.findViewById(R.id.home_weekdays)
        gridView = view.findViewById(R.id.home_grid)
        listView = view.findViewById(R.id.home_list)
        emptyView = view.findViewById(R.id.home_empty)

        gridAdapter = MonthGridAdapter { julian -> applySelection(julian, true) }
        gridView.layoutManager = GridLayoutManager(requireContext(), 7)
        gridView.adapter = gridAdapter
        gridView.itemAnimator = null
        gridView.overScrollMode = View.OVER_SCROLL_NEVER
        gridView.isNestedScrollingEnabled = false

        listAdapter = HomeEventsAdapter(requireContext(), zone.id) { event -> openEvent(event) }
        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = listAdapter

        view.findViewById<ImageButton>(R.id.home_prev).setOnClickListener { shiftMonth(-1) }
        view.findViewById<ImageButton>(R.id.home_next).setOnClickListener { shiftMonth(1) }
        view.findViewById<View>(R.id.home_today).setOnClickListener { goToToday() }
        toggleButton.setOnClickListener {
            gridCollapsed = !gridCollapsed
            userSetCollapse = true
            applyGridVisibility()
        }

        buildWeekdayHeader()
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
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
        val time = event.selectedTime ?: event.startTime ?: return
        applySelection(julianOf(time.toMillis()), false)
    }

    override fun eventsChanged() {
        if (!isViewReady()) return
        refreshMonth(reload = true)
        loadDay()
    }

    /** Snapshot used by MainActivity to restore this view after recreation. */
    fun saveHomeState(): Bundle {
        val bundle = Bundle()
        bundle.putInt(KEY_SELECTED, selectedJulian)
        bundle.putInt(KEY_DISPLAY_YM, displayYear * 12 + displayMonth)
        bundle.putBoolean(KEY_COLLAPSED, gridCollapsed)
        bundle.putBoolean(KEY_USER_SET_COLLAPSE, userSetCollapse)
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
        refreshMonth(reload = true)
    }

    private fun goToToday() {
        applySelection(julianOf(System.currentTimeMillis()), true)
    }

    private fun refreshMonth(reload: Boolean) {
        if (!isViewReady()) return
        titleView.text = monthTitle()
        if (reload) monthData = null
        renderGrid()
        if (reload) loadMonth()
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
        val lp = gridView.layoutParams
        lp.height = rowCount * cellHeightPx
        gridView.layoutParams = lp
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
            base + ", " + resources.getQuantityString(R.plurals.home_events_count, count, count)
        } else {
            base
        }
    }

    private fun monthTitle(): String {
        val locale = Locale.getDefault()
        val format = monthTitleFormat ?: SimpleDateFormat(
            AndroidDateFormat.getBestDateTimePattern(locale, "yMMMM"), locale
        ).apply { timeZone = zone }.also { monthTitleFormat = it }
        val date = GregorianCalendar(zone).apply { clear(); set(displayYear, displayMonth, 1) }.time
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
     * Chooses wide (side-by-side) or narrow (stacked) layout from the window
     * size, not from the device or orientation. Short windows collapse the grid
     * unless the user has chosen otherwise.
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
            root.gravity = Gravity.NO_GRAVITY
            body.orientation = LinearLayout.HORIZONTAL
            body.layoutParams = LinearLayout.LayoutParams(matchParent, 0, 1f)
            gridContainer.layoutParams = LinearLayout.LayoutParams(
                minOf(bodyWidth / 2, dp(MAX_GRID_WIDTH_DP)), matchParent
            )
            listFrame.layoutParams = LinearLayout.LayoutParams(0, matchParent, 1f)
            cellHeightPx = ((bodyHeight - dp(WEEKDAY_HEIGHT_DP)) / 6).coerceIn(minCell, maxCell)
        } else {
            root.gravity = Gravity.CENTER_HORIZONTAL
            body.orientation = LinearLayout.VERTICAL
            body.layoutParams = LinearLayout.LayoutParams(
                minOf(bodyWidth, dp(MAX_STACK_WIDTH_DP)), 0, 1f
            )
            gridContainer.layoutParams = LinearLayout.LayoutParams(
                matchParent, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            listFrame.layoutParams = LinearLayout.LayoutParams(matchParent, 0, 1f)
            cellHeightPx = ((bodyHeight * 0.5f) / 6).toInt().coerceIn(minCell, maxCell)
            if (!userSetCollapse) {
                gridCollapsed = bodyHeight < dp(SHORT_WINDOW_DP)
            }
        }
        applyGridVisibility()
        renderGrid()
    }

    private fun applyGridVisibility() {
        gridContainer.visibility = if (gridCollapsed) View.GONE else View.VISIBLE
        toggleButton.setImageResource(
            if (gridCollapsed) R.drawable.ic_expand_small else R.drawable.ic_collapse_small
        )
        toggleButton.contentDescription = getString(R.string.home_toggle_month)
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
        const val ARG_TIME = "home_time"
        const val ARG_STATE = "home_state"
        private const val KEY_SELECTED = "home_selected"
        private const val KEY_DISPLAY_YM = "home_display_ym"
        private const val KEY_LIST_POSITION = "home_list_position"
        private const val KEY_COLLAPSED = "home_collapsed"
        private const val KEY_USER_SET_COLLAPSE = "home_user_set_collapse"

        private const val WIDE_MIN_WIDTH_DP = 600f
        private const val MIN_CELL_DP = 44
        private const val MAX_CELL_DP = 64
        private const val WEEKDAY_HEIGHT_DP = 28
        private const val MAX_GRID_WIDTH_DP = 560
        private const val MAX_STACK_WIDTH_DP = 640
        private const val SHORT_WINDOW_DP = 520

        @JvmStatic
        fun newInstance(timeMillis: Long, state: Bundle?): HomeMonthDayFragment =
            HomeMonthDayFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TIME, timeMillis)
                    if (state != null) putBundle(ARG_STATE, state)
                }
            }
    }
}
