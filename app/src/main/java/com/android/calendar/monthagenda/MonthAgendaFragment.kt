/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract.Attendees
import android.text.format.DateFormat as AndroidDateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.AllInOneActivity
import com.android.calendar.CalendarController
import com.android.calendar.CalendarController.EventType
import com.android.calendar.Event
import com.android.calendar.lunar.LunarCache
import com.android.calendar.Utils
import com.android.calendar.lunar.LunarHelper
import com.android.calendar.month.CellSlotMath
import com.android.calendar.month.MonthGridAdapter
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import ws.xsoh.etar.R

/**
 * The month entry in its 月历＋日程 layout: the month grid above the schedule of the
 * selected day.
 *
 * - Tapping a date only selects it and refreshes the list. It never switches view or
 *   creates an event.
 * - Left or right swipe on the grid flips the month.
 * - The handle at the top of the schedule area collapses or expands the grid. Drag up
 *   collapses, drag down expands, and a tap toggles. Expanding and collapsing never
 *   covers the toolbar and never opens a dialog.
 * - The browsing month is shown in the toolbar; this fragment has no header of its own.
 *
 * The selected date is mirrored into the CalendarController time so that the floating
 * action button and other views use it.
 */
class MonthAgendaFragment : Fragment(), CalendarController.EventHandler {

    private lateinit var controller: CalendarController
    private var loader: MonthAgendaLoader? = null
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
    // Whether the grid reserves a lunar slot. Both enabled modes do; the day text is per day.
    private var lunarOn = false
    // Everything that changes the lunar text. When it changes, old labels are dropped at once.
    private var lunarKey = ""
    private var dayTitleFormat: SimpleDateFormat? = null
    private var cellDateFormat: SimpleDateFormat? = null

    // Expand state. The user's choice and the window-size rule are kept apart: the
    // user's choice (null = never chosen) persists, the auto rule is recomputed on each
    // layout pass and never persisted.
    private var userExpanded: Boolean? = null
    private var autoCollapsed = false
    private var wideLayout = false

    // 0 = collapsed (one row, the selected week), 1 = expanded (all rows).
    private var progress = 1f
    private var expandAnimator: ValueAnimator? = null
    private val animationGate = RequestGate()

    // Views are held through viewRef(). onDestroyView clears every one of them, so the fragment
    // never keeps a destroyed view, and an access while no view is attached fails loudly.
    private val viewRefs = ArrayList<ViewRef<*>>()
    private var viewBound = false
    private var viewGeneration = 0
    private var contentStale = false
    private var lunarShown = false

    private var body: LinearLayout by viewRef<LinearLayout>()
    private var gridColumn: LinearLayout by viewRef<LinearLayout>()
    private var dayColumn: LinearLayout by viewRef<LinearLayout>()
    private var gridClip: View by viewRef<View>()
    private var handle: View by viewRef<View>()
    private var dayTitle: TextView by viewRef<TextView>()
    private var weekdayRow: LinearLayout by viewRef<LinearLayout>()
    private var gridView: RecyclerView by viewRef<RecyclerView>()
    private var listView: RecyclerView by viewRef<RecyclerView>()
    private var emptyView: TextView by viewRef<TextView>()
    private var gridAdapter: MonthGridAdapter by viewRef<MonthGridAdapter>()
    private var listAdapter: MonthAgendaAdapter by viewRef<MonthAgendaAdapter>()
    private var touchSlopPx = 0
    private val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
        val width = right - left
        val height = bottom - top
        if (width != lastWidth || height != lastHeight) applyAdaptiveLayout(width, height)
    }

    // Handle gesture state. Coordinates are raw screen values, so moving the handle does not feed back.
    private var handleActive = false
    private var handlePointerId = MotionEvent.INVALID_POINTER_ID
    private var handleDownRawY = 0f
    private var handleStartProgress = 0f
    private var handleDragging = false
    private var handleVelocity: VelocityTracker? = null

    private val monthSwipe = MonthSwipeListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        controller = CalendarController.getInstance(context)
        zone = TimeZone.getTimeZone(Utils.getTimeZone(context, null))
        firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(context)
        lunarOn = LunarHelper.needsLunarSlot(Utils.getLunarMode(context))
        lunarKey = lunarSettingsKey(context)

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
        if (state != null && state.containsKey(KEY_USER_EXPANDED)) {
            userExpanded = when (state.getInt(KEY_USER_EXPANDED)) {
                1 -> true
                0 -> false
                else -> null
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_month_agenda, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewGeneration++
        loader = MonthAgendaLoader(requireContext())
        body = view.findViewById(R.id.agenda_body)
        gridColumn = view.findViewById(R.id.agenda_grid_column)
        dayColumn = view.findViewById(R.id.agenda_day_column)
        gridClip = view.findViewById(R.id.agenda_grid_clip)
        handle = view.findViewById(R.id.agenda_handle)
        dayTitle = view.findViewById(R.id.agenda_day_title)
        weekdayRow = view.findViewById(R.id.agenda_weekdays)
        gridView = view.findViewById(R.id.agenda_grid)
        listView = view.findViewById(R.id.agenda_list)
        emptyView = view.findViewById(R.id.agenda_empty)
        touchSlopPx = ViewConfiguration.get(requireContext()).scaledTouchSlop
        viewBound = true

        gridAdapter = MonthGridAdapter { julian -> applySelection(julian, true) }
        gridView.layoutManager = GridLayoutManager(requireContext(), 7)
        gridView.adapter = gridAdapter
        gridView.itemAnimator = null
        gridView.overScrollMode = View.OVER_SCROLL_NEVER
        gridView.isNestedScrollingEnabled = false
        gridView.addOnItemTouchListener(monthSwipe)

        // Previous and next accessibility actions on the grid, for TalkBack and switch users. The
        // labels follow the mode: a week while collapsed, a month while expanded. One delegate
        // reads the mode each time, so the labels stay right after a change.
        gridClip.isFocusable = true
        gridClip.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val week = isWeekNavigation()
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        R.id.agenda_action_previous,
                        getString(if (week) R.string.agenda_previous_week else R.string.agenda_previous_month)
                    )
                )
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        R.id.agenda_action_next,
                        getString(if (week) R.string.agenda_next_week else R.string.agenda_next_month)
                    )
                )
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean =
                when (action) {
                    R.id.agenda_action_previous -> {
                        navigate(-1)
                        true
                    }
                    R.id.agenda_action_next -> {
                        navigate(1)
                        true
                    }
                    else -> super.performAccessibilityAction(host, action, args)
                }
        })

        listAdapter = MonthAgendaAdapter(requireContext(), zone.id) { event -> openEvent(event) }
        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = listAdapter

        setUpHandle()
        buildWeekdayHeader()
        body.addOnLayoutChangeListener(layoutListener)
        refreshMonth(reload = true)
        loadDay()
    }

    override fun onResume() {
        super.onResume()
        // Only a return from another screen triggers a reload (the first resume after the view was
        // created is already loaded). Nothing is rebuilt: the month, the selection and the list
        // position stay as they are, and only data that can change is read again.
        if (!isViewReady() || !contentStale) return
        contentStale = false
        LunarCache.evictAll()
        val context = requireContext()
        val key = lunarSettingsKey(context)
        if (key != lunarKey) {
            // The lunar setting changed (mode, festivals, solar terms or full label). Old labels are
            // dropped now, so no stale text shows. The month, selection and list position stay.
            lunarKey = key
            monthData = null
            lunarOn = LunarHelper.needsLunarSlot(Utils.getLunarMode(context))
            relayout()
        }
        loadMonth()
        pendingListPosition = (listView.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()?.coerceAtLeast(0) ?: 0
        loadDay(keepVisible = true)
    }

    override fun onPause() {
        // Finish an expand or collapse that is still running, so the saved state matches the screen.
        expandAnimator?.end()
        contentStale = true
        super.onPause()
    }

    override fun onDestroyView() {
        cancelExpandAnimation()
        handleVelocity?.recycle()
        handleVelocity = null
        handleActive = false
        handleDragging = false
        monthSwipe.reset()
        // Results still in flight belong to this view. Bumping the generation drops them, and
        // shutting the loader down stops its worker and pending deliveries.
        viewGeneration++
        loader?.shutdown()
        loader = null
        if (viewBound) {
            gridView.removeOnItemTouchListener(monthSwipe)
            gridView.adapter = null
            listView.adapter = null
            body.removeOnLayoutChangeListener(layoutListener)
            handle.setOnTouchListener(null)
            handle.setOnClickListener(null)
            handle.setOnKeyListener(null)
        }
        viewBound = false
        viewRefs.forEach { it.clear() }
        super.onDestroyView()
    }

    override fun onDestroy() {
        cancelExpandAnimation()
        handleVelocity?.recycle()
        handleVelocity = null
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
        bundle.putInt(
            KEY_USER_EXPANDED,
            when (userExpanded) {
                true -> 1
                false -> 0
                null -> -1
            }
        )
        val position = if (pendingListPosition >= 0) {
            pendingListPosition
        } else if (isViewReady()) {
            (listView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        } else {
            0
        }
        bundle.putInt(KEY_LIST_POSITION, maxOf(position, 0))
        return bundle
    }

    private fun isViewReady(): Boolean = isAdded && view != null && viewBound

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

    /**
     * True while the grid is collapsed to the selected week (narrow layout only). The check uses
     * the same rule as the expanded state, so a window downgrade counts as collapsed too.
     */
    private fun isWeekNavigation(): Boolean =
        !wideLayout && !PanelMath.isExpanded(userExpanded, autoCollapsed)

    /** Swipe and accessibility step: a week while collapsed, a month while expanded. */
    private fun navigate(delta: Int) {
        if (isWeekNavigation()) shiftWeek(delta) else shiftMonth(delta)
    }

    /**
     * Moves the selection by whole weeks on the same weekday, using civil days. The browse month
     * follows the selection, so the collapsed grid always shows the selected week, and expanding
     * shows the month of the selected day.
     */
    private fun shiftWeek(delta: Int) {
        if (!isViewReady()) return
        selectedJulian = MonthGridMath.shiftWeeks(selectedJulian, delta)
        val ymd = MonthGridMath.ymd(selectedJulian)
        val monthChanged = ymd[0] != displayYear || ymd[1] != displayMonth + 1
        displayYear = ymd[0]
        displayMonth = ymd[1] - 1
        refreshMonth(reload = monthChanged)
        loadDay()
        sendSelectionToController()
    }

    private fun shiftMonth(delta: Int) {
        if (!isViewReady()) return
        val index = displayYear * 12 + displayMonth + delta
        displayYear = index / 12
        displayMonth = index % 12
        // Keep the same day of month when the new month has it, else its last day.
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

    /** The toolbar is the only place that shows the browsing month. */
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
        val generation = viewGeneration
        loader?.loadMonth(first, rowCount * 7) { data ->
            if (generation == viewGeneration && isViewReady() && data.firstJulian == monthFirstCell) {
                monthData = data
                renderGrid()
            }
        }
    }

    /** Loads the schedule of the selected day. [keepVisible] keeps the current cards until the new ones arrive. */
    private fun loadDay(keepVisible: Boolean = false) {
        if (!isViewReady()) return
        val julian = selectedJulian
        val generation = viewGeneration
        dayTitle.text = dayTitleText(julian)
        if (!keepVisible) {
            listAdapter.submit(emptyList())
            emptyView.visibility = View.GONE
        }
        loader?.loadDay(julian) { items ->
            if (generation == viewGeneration && isViewReady() && julian == selectedJulian) {
                listAdapter.submit(items)
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                if (pendingListPosition >= 0 && items.isNotEmpty()) {
                    listView.scrollToPosition(minOf(pendingListPosition, items.size - 1))
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
                    lunarText = data?.lunarLabels?.get(julian)?.text,
                    lunarEmphasized = data?.lunarLabels?.get(julian)?.emphasized == true,
                    eventColors = data?.dayColors?.get(julian).orEmpty(),
                    description = describeDay(ymd, count),
                    lunarEnabled = lunarShown
                )
            )
        }
        gridView.layoutParams = gridView.layoutParams.apply { height = rowCount * cellHeightPx }
        gridAdapter.submitCells(cells, cellHeightPx)
        gridClip.contentDescription = monthTitleText()
        applyPanelProgress()
    }

    /** Every preference that changes the lunar text of a day, as one comparable key. */
    private fun lunarSettingsKey(context: Context): String {
        val festivals = Utils.getEnabledLunarFestivals(context).sorted().joinToString(",")
        return listOf(
            Utils.getLunarMode(context).name,
            festivals,
            Utils.getShowLunarJieqi(context).toString(),
            Utils.getLunarDetailAlways(context).toString()
        ).joinToString("|")
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

    /** Browsing month as text, used as the grid's accessible name. */
    private fun monthTitleText(): String {
        val format = SimpleDateFormat(
            AndroidDateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMM"), Locale.getDefault()
        ).apply { timeZone = zone }
        return format.format(GregorianCalendar(zone).apply { clear(); set(displayYear, displayMonth, 1) }.time)
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

    /** Re-runs the layout from the current body size, for example after a setting changed. */
    private fun relayout() {
        lastWidth = -1
        lastHeight = -1
        applyAdaptiveLayout(body.width, body.height)
    }

    /**
     * Chooses the wide (side-by-side) or narrow (stacked) layout from the window size, not from
     * the device or orientation. The narrow layout has the handle; the wide layout does not,
     * because the month is always fully shown there. The row height is never below what the
     * slots need at this font scale. When the expanded grid would leave the schedule list too
     * little room, lunar labels are hidden first, then the grid is auto-collapsed. The user's
     * choice is not changed by either degradation.
     */
    private fun applyAdaptiveLayout(width: Int, height: Int) {
        if (!isViewReady() || body.width <= 0 || body.height <= 0) return
        lastWidth = width
        lastHeight = height
        val density = resources.displayMetrics.density
        val fontScale = resources.configuration.fontScale
        wideLayout = width / density >= WIDE_MIN_WIDTH_DP && width > height
        val bodyWidth = body.width
        val bodyHeight = body.height
        val maxCell = dp(MAX_CELL_DP)
        val matchParent = LinearLayout.LayoutParams.MATCH_PARENT
        // Fixed chrome is measured where it has text, so a large font is counted.
        val weekdayPx = weekdayRow.height.takeIf { it > 0 } ?: dp(WEEKDAY_HEIGHT_DP)
        val lunarRow = CellSlotMath.minRowHeightPx(density, fontScale, true)
        val plainRow = CellSlotMath.minRowHeightPx(density, fontScale, false)

        if (wideLayout) {
            cancelExpandAnimation()
            val plan = PanelMath.planLayout(bodyHeight, weekdayPx, 0, ROWS, lunarRow, plainRow, lunarOn)
            lunarShown = plan.lunarShown
            val rowMin = if (lunarShown) lunarRow else plainRow
            body.orientation = LinearLayout.HORIZONTAL
            body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).apply {
                this.width = matchParent
                this.height = matchParent
            }
            gridColumn.layoutParams = LinearLayout.LayoutParams(
                minOf(bodyWidth / 2, dp(MAX_GRID_WIDTH_DP)), LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dayColumn.layoutParams = LinearLayout.LayoutParams(0, matchParent, 1f)
            handle.visibility = View.GONE
            autoCollapsed = false
            cellHeightPx = ((bodyHeight - weekdayPx) / ROWS).coerceIn(rowMin, maxOf(rowMin, maxCell))
            progress = 1f
        } else {
            body.orientation = LinearLayout.VERTICAL
            body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).apply {
                this.width = matchParent
                this.height = matchParent
            }
            gridColumn.layoutParams = LinearLayout.LayoutParams(
                matchParent, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dayColumn.layoutParams = LinearLayout.LayoutParams(matchParent, 0, 1f)
            handle.visibility = View.VISIBLE
            val titlePx = dayTitle.height.takeIf { it > 0 } ?: dp(TITLE_HEIGHT_DP)
            val chromePx = weekdayPx + dp(HANDLE_DP) + titlePx
            val listMinPx = dp(LIST_MIN_DP)
            val plan = PanelMath.planLayout(bodyHeight, chromePx, listMinPx, ROWS, lunarRow, plainRow, lunarOn)
            lunarShown = plan.lunarShown
            autoCollapsed = plan.autoCollapsed
            val rowMin = if (lunarShown) lunarRow else plainRow
            // Half of the body, but never more than what the list leaves over.
            val gridCap = minOf(bodyHeight * 0.5f, (bodyHeight - chromePx - listMinPx).toFloat())
            cellHeightPx = ((gridCap - weekdayPx) / ROWS).toInt().coerceIn(rowMin, maxOf(rowMin, maxCell))
            cancelExpandAnimation()
            progress = if (PanelMath.isExpanded(userExpanded, autoCollapsed)) 1f else 0f
        }
        renderGrid()
        updateHandleSemantics()
    }

    // ---- Expand and collapse -------------------------------------------------------------

    private fun setUpHandle() {
        handle.isFocusable = true
        handle.isClickable = true
        handle.contentDescription = getString(R.string.agenda_handle_description)
        handle.setOnClickListener { setExpanded(progress < 0.5f) }
        handle.setOnTouchListener { v, e -> onHandleTouch(v, e) }
        handle.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN || !handle.isEnabled || wideLayout) {
                return@setOnKeyListener false
            }
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    setExpanded(false)
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    setExpanded(true)
                    true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> {
                    setExpanded(progress < 0.5f)
                    true
                }
                else -> false
            }
        }
        ViewCompat.addAccessibilityAction(handle, getString(R.string.agenda_handle_toggle_action)) { _, _ ->
            setExpanded(progress < 0.5f)
            true
        }
    }

    private fun onHandleTouch(v: View, e: MotionEvent): Boolean {
        if (!isViewReady() || wideLayout) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A new touch takes over a running animation at its current position.
                // Takes over at the current visual position; the cancelled animation cannot jump.
                cancelExpandAnimation()
                handleActive = true
                handlePointerId = e.getPointerId(0)
                handleDownRawY = e.rawY
                handleStartProgress = progress
                handleDragging = false
                handleVelocity?.recycle()
                handleVelocity = VelocityTracker.obtain().also { it.addMovement(e) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!handleActive) return false
                handleVelocity?.addMovement(e)
                val index = e.findPointerIndex(handlePointerId)
                if (index < 0) return true
                val dy = rawY(e, index) - handleDownRawY
                if (!handleDragging && abs(dy) > touchSlopPx.toFloat()) {
                    handleDragging = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (handleDragging) {
                    setProgress(PanelMath.progressFromDrag(handleStartProgress, dy, panelSpanPx()))
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger ends the gesture by position. It never starts a second one.
                if (handleActive && handleDragging) {
                    snapAfterDrag(0f)
                }
                handleActive = false
                handleDragging = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!handleActive) return false
                if (handleDragging) {
                    handleVelocity?.addMovement(e)
                    handleVelocity?.computeCurrentVelocity(1000)
                    val vy = handleVelocity?.getYVelocity(handlePointerId) ?: 0f
                    snapAfterDrag(vy)
                } else {
                    // A tap goes through performClick, the same path as TalkBack's click.
                    v.performClick()
                }
                resetHandleGesture(v)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (handleDragging) snapAfterDrag(0f)
                resetHandleGesture(v)
                return true
            }
            else -> return handleActive
        }
    }

    private fun resetHandleGesture(v: View) {
        v.parent?.requestDisallowInterceptTouchEvent(false)
        handleActive = false
        handleDragging = false
        handlePointerId = MotionEvent.INVALID_POINTER_ID
        handleVelocity?.recycle()
        handleVelocity = null
    }

    /** Snaps after a drag, by position or by fling velocity, and stores the result as the user's choice. */
    private fun snapAfterDrag(velocityPxPerSec: Float) {
        val target = PanelMath.snapTarget(progress, velocityPxPerSec, dp(FLING_DP).toFloat())
        setExpanded(target >= 0.5f)
    }

    /** Explicit expand or collapse (handle tap, drag release, keyboard, accessibility). */
    private fun setExpanded(expanded: Boolean) {
        if (!isViewReady() || wideLayout) return
        userExpanded = expanded
        animateProgress(if (expanded) 1f else 0f)
        updateHandleSemantics()
    }

    private fun animateProgress(requested: Float) {
        val target = minOf(requested, PanelMath.maxProgress(autoCollapsed))
        cancelExpandAnimation()
        val startValue = progress
        if (startValue == target) return
        val animationsOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        if (!animationsOn) {
            setProgress(target)
            return
        }
        val token = animationGate.begin()
        expandAnimator = ValueAnimator.ofFloat(startValue, target).apply {
            duration = (EXPAND_MS * abs(target - startValue)).toLong().coerceAtLeast(MIN_EXPAND_MS)
            interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            addUpdateListener { anim ->
                if (animationGate.isCurrent(token)) setProgress(anim.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Only the current animator may finish. A cancelled or replaced one returns here,
                    // so it cannot jump to its old target or clear a newer reference.
                    if (!animationGate.isCurrent(token) || expandAnimator !== animation) return
                    expandAnimator = null
                    setProgress(target)
                }
            })
            start()
        }
    }

    /**
     * Stops the running animation and keeps the current visual progress. The gate is advanced
     * before cancelling, because a cancelled animator still reports onAnimationEnd.
     */
    private fun cancelExpandAnimation() {
        animationGate.begin()
        expandAnimator?.cancel()
        expandAnimator = null
    }

    private fun setProgress(value: Float) {
        progress = minOf(PanelMath.clamp01(value), PanelMath.maxProgress(autoCollapsed))
        applyPanelProgress()
        updateHandleSemantics()
    }

    /** Grid viewport height and offset for the current progress. */
    private fun applyPanelProgress() {
        if (!viewBound || cellHeightPx <= 0) return
        gridClip.layoutParams = gridClip.layoutParams.apply {
            height = PanelMath.gridHeightPx(progress, cellHeightPx, rowCount)
        }
        val selectedRow = PanelMath.selectedRow(selectedJulian, monthFirstCell, rowCount)
        gridView.translationY = PanelMath.gridTranslationPx(progress, selectedRow, cellHeightPx)
    }

    private fun panelSpanPx(): Float = maxOf(1, (rowCount - 1) * cellHeightPx).toFloat()

    private fun updateHandleSemantics() {
        if (!viewBound) return
        val expanded = progress >= 0.5f
        ViewCompat.setStateDescription(
            handle,
            getString(if (expanded) R.string.agenda_state_expanded else R.string.agenda_state_collapsed)
        )
    }

    // ---- Month swipe ---------------------------------------------------------------------

    /**
     * Horizontal fling or drag on the grid flips the month. The gesture starts only past the
     * touch slop and when it is clearly horizontal. Touches near the screen edges are left alone
     * so that the navigation drawer's edge gesture still works. A second finger cancels the
     * swipe. Cells still receive taps, because the gesture is intercepted only once it is a swipe.
     */
    private inner class MonthSwipeListener : RecyclerView.OnItemTouchListener {
        private var tracking = false
        private var swiping = false
        private var multiTouch = false
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var downRawX = 0f
        private var downRawY = 0f
        private var velocity: VelocityTracker? = null

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    reset()
                    // Edges of the window, not of the screen: in split-screen the drawer edge is the window's.
                    val edge = dp(EDGE_EXCLUDE_DP)
                    val root = rv.rootView
                    val location = IntArray(2)
                    root.getLocationOnScreen(location)
                    val windowLeft = location[0]
                    if (e.rawX < windowLeft + edge || e.rawX > windowLeft + root.width - edge) return false
                    tracking = true
                    pointerId = e.getPointerId(0)
                    downRawX = e.rawX
                    downRawY = e.rawY
                    velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                    return false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    multiTouch = true
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking || multiTouch) return false
                    velocity?.addMovement(e)
                    val index = e.findPointerIndex(pointerId)
                    if (index < 0) return false
                    val dx = rawX(e, index) - downRawX
                    val dy = rawY(e, index) - downRawY
                    if (PanelMath.isHorizontalSwipe(dx, dy, touchSlopPx.toFloat())) {
                        swiping = true
                        return true
                    }
                    return false
                }
                else -> return false
            }
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            if (!swiping) return
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> velocity?.addMovement(e)
                MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
                MotionEvent.ACTION_UP -> {
                    velocity?.addMovement(e)
                    velocity?.computeCurrentVelocity(1000)
                    val index = e.findPointerIndex(pointerId)
                    val dx = if (index >= 0) rawX(e, index) - downRawX else 0f
                    val vx = velocity?.getXVelocity(pointerId) ?: 0f
                    val delta = if (multiTouch) {
                        0
                    } else {
                        PanelMath.swipeMonthDelta(dx, vx, dp(SWIPE_MIN_DP).toFloat(), dp(FLING_DP).toFloat())
                    }
                    reset()
                    if (delta != 0) navigate(delta)
                }
                MotionEvent.ACTION_CANCEL -> reset()
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

        fun reset() {
            tracking = false
            swiping = false
            multiTouch = false
            pointerId = MotionEvent.INVALID_POINTER_ID
            velocity?.recycle()
            velocity = null
        }
    }

    // ---- Events and time -----------------------------------------------------------------

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
     * Mirrors the selected day into the controller time so that the floating action button
     * prefills it and switching views keeps it. setTime does not dispatch an event, so the
     * activity's view-switching and animations are not triggered.
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

    /**
     * Screen coordinates of pointer [index]. MotionEvent.getRawX(int) and getRawY(int) need
     * API 29, and minSdk is 23, so they are derived from the view-local value and the event's
     * raw/local pair. The result is in screen space, so a moving handle does not feed back.
     */
    private fun rawX(e: MotionEvent, index: Int): Float = PanelMath.rawCoordinate(e.getX(index), e.rawX, e.x)

    private fun rawY(e: MotionEvent, index: Int): Float = PanelMath.rawCoordinate(e.getY(index), e.rawY, e.y)

    /** Delegate for a view reference that onDestroyView can clear. */
    private fun <T : Any> viewRef(): ReadWriteProperty<Any?, T> = ViewRef<T>().also { viewRefs.add(it) }

    private class ViewRef<T : Any> : ReadWriteProperty<Any?, T> {
        private var value: T? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): T =
            value ?: throw IllegalStateException("${property.name} is not attached to a view")

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
        }

        fun clear() {
            value = null
        }
    }

    companion object {
        const val ARG_TIME = "month_agenda_time"
        const val ARG_STATE = "month_agenda_state"
        private const val KEY_SELECTED = "month_agenda_selected"
        private const val KEY_DISPLAY_YM = "month_agenda_display_ym"
        private const val KEY_LIST_POSITION = "month_agenda_list_position"
        private const val KEY_USER_EXPANDED = "month_agenda_user_expanded"

        private const val WIDE_MIN_WIDTH_DP = 600f
        private const val MAX_CELL_DP = 64
        private const val WEEKDAY_HEIGHT_DP = 28
        private const val MAX_GRID_WIDTH_DP = 560
        private const val HANDLE_DP = 48
        private const val TITLE_HEIGHT_DP = 48
        /** Experimental: the least room the schedule list keeps when the grid is expanded. */
        private const val LIST_MIN_DP = 120
        private const val ROWS = 6
        private const val EDGE_EXCLUDE_DP = 24
        private const val SWIPE_MIN_DP = 64
        private const val FLING_DP = 800
        private const val EXPAND_MS = 240L
        private const val MIN_EXPAND_MS = 80L

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
