package com.android.calendar.shift

import android.database.Cursor
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.AsyncQueryService
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftOverride
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ws.xsoh.etar.R
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

class ShiftSchedulerFragment : Fragment() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var presetsRecycler: RecyclerView
    private lateinit var presetsAdapter: ShiftPresetsAdapter

    // MD3 Calendar Views
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var txtCurrentMonth: TextView
    private lateinit var weekdayHeadersContainer: LinearLayout
    private lateinit var calendarGridRecycler: RecyclerView
    private lateinit var calendarAdapter: ShiftCalendarAdapter

    // Action Buttons inside Bottom Panel
    private lateinit var btnPaintMode: MaterialButton
    private lateinit var btnClearOverrides: MaterialButton
    private lateinit var btnAutoFill: MaterialButton
    private lateinit var btnSave: MaterialButton

    private var selectedCalendarId: Long = -1
    private var anchorJulianDay: Int = 0
    private var selectedJulianDay: Int = -1

    private var allPresets: Map<Long, ShiftPreset> = emptyMap()
    private var activeRule: ShiftRotationRule? = null
    private var allOverrides: MutableMap<Int, Long> = mutableMapOf()

    private var paintModeEnabled = false
    private var lastPaintedJd = -1
    private var lastToastTime = 0L
    private val currentDisplayedMonth = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = Time()
        t.set(System.currentTimeMillis())
        anchorJulianDay = Time.getJulianDay(t.toMillis(), t.getGmtOffset())
        selectedJulianDay = anchorJulianDay
        currentDisplayedMonth.timeInMillis = System.currentTimeMillis()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shift_scheduler, container, false)

        calendarSpinner = view.findViewById(R.id.calendar_spinner)
        presetsRecycler = view.findViewById(R.id.presets_recycler)

        // Bind MD3 Calendar views
        btnPrevMonth = view.findViewById(R.id.btn_prev_month)
        btnNextMonth = view.findViewById(R.id.btn_next_month)
        txtCurrentMonth = view.findViewById(R.id.txt_current_month)
        weekdayHeadersContainer = view.findViewById(R.id.weekday_headers_container)
        calendarGridRecycler = view.findViewById(R.id.calendar_grid_recycler)

        // Bind Action Buttons
        btnPaintMode = view.findViewById(R.id.btn_paint_mode)
        btnClearOverrides = view.findViewById(R.id.btn_clear_overrides)
        btnAutoFill = view.findViewById(R.id.btn_auto_fill)
        btnSave = view.findViewById(R.id.btn_save)

        // Month switching click handlers
        btnPrevMonth.setOnClickListener {
            currentDisplayedMonth.add(Calendar.MONTH, -1)
            updateMonthLabel()
            updateGridSelection()
        }
        btnNextMonth.setOnClickListener {
            currentDisplayedMonth.add(Calendar.MONTH, 1)
            updateMonthLabel()
            updateGridSelection()
        }

        btnPaintMode.setOnClickListener { togglePaintMode(!paintModeEnabled) }
        btnClearOverrides.setOnClickListener { clearOverrides() }
        btnSave.setOnClickListener { saveShiftsToCalendar() }
        btnAutoFill.setOnClickListener { showWizardDialog() }

        view.findViewById<Button>(R.id.btn_add_preset).setOnClickListener { showPresetDialog(null) }

        setupWeekdayHeaders()
        setupCalendarGrid()
        setupPresets()
        setupCalendarSpinner()
        observeData()
        updateMonthLabel()

        return view
    }

    private fun setupWeekdayHeaders() {
        weekdayHeadersContainer.removeAllViews()
        val firstDayOfWeek = com.android.calendar.Utils.getFirstDayOfWeekAsCalendar(requireContext())
        val symbols = DateFormatSymbols.getInstance().shortWeekdays

        for (i in 0 until 7) {
            val dayIndex = ((firstDayOfWeek + i - 1) % 7) + 1
            val weekdayName = symbols[dayIndex]

            val shortName = if (Locale.getDefault().language == "zh") {
                weekdayName.replace("星期", "").replace("周", "")
            } else {
                weekdayName.firstOrNull()?.toString() ?: ""
            }

            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = android.view.Gravity.CENTER
                text = shortName
                textSize = 12f
                setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            weekdayHeadersContainer.addView(textView)
        }
    }

    private fun setupCalendarGrid() {
        calendarAdapter = ShiftCalendarAdapter(
            requireContext(),
            onDayClicked = { cell ->
                selectedJulianDay = cell.julianDay
                updateGridSelection()
            },
            onDayPainted = { cell ->
                handlePaintTap(cell.julianDay)
            }
        )
        calendarGridRecycler.adapter = calendarAdapter

        // Register Touch Listener to capture dragging over grid items for seamless painting
        calendarGridRecycler.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (!paintModeEnabled) return false
                val action = e.action
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    val child = rv.findChildViewUnder(e.x, e.y)
                    if (child != null) {
                        val position = rv.getChildAdapterPosition(child)
                        if (position != RecyclerView.NO_POSITION) {
                            val cell = calendarAdapter.getDays().getOrNull(position)
                            if (cell != null) {
                                handlePaintTap(cell.julianDay)
                            }
                        }
                    }
                    return true // Intercept touch to prevent calendar/scroll list scrolling during drag-painting
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    lastPaintedJd = -1
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (!paintModeEnabled) return
                val action = e.action
                if (action == MotionEvent.ACTION_MOVE) {
                    val child = rv.findChildViewUnder(e.x, e.y)
                    if (child != null) {
                        val position = rv.getChildAdapterPosition(child)
                        if (position != RecyclerView.NO_POSITION) {
                            val cell = calendarAdapter.getDays().getOrNull(position)
                            if (cell != null) {
                                handlePaintTap(cell.julianDay)
                            }
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    lastPaintedJd = -1
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun updateMonthLabel() {
        val locale = Locale.getDefault()
        val sdf = SimpleDateFormat("LLLL yyyy", locale)
        val textStr = sdf.format(currentDisplayedMonth.time)
        txtCurrentMonth.text = textStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    private fun getDaysForMonth(year: Int, month: Int, firstDayOfWeek: Int): List<Calendar> {
        val days = ArrayList<Calendar>()
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfMonthOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        var offset = firstDayOfMonthOfWeek - firstDayOfWeek
        if (offset < 0) {
            offset += 7
        }

        cal.add(Calendar.DAY_OF_MONTH, -offset)

        // Generate exactly 42 cells (6 rows * 7 columns)
        for (i in 0 until 42) {
            days.add(cal.clone() as Calendar)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return days
    }

    private fun togglePaintMode(enabled: Boolean) {
        paintModeEnabled = enabled
        calendarAdapter.setPaintMode(enabled)

        val primaryColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary)

        if (enabled) {
            btnPaintMode.setBackgroundColor(primaryColor)
            btnPaintMode.setTextColor(Color.WHITE)
            btnPaintMode.iconTint = ColorStateList.valueOf(Color.WHITE)
            btnPaintMode.strokeColor = ColorStateList.valueOf(primaryColor)

            if (presetsAdapter.getSelectedPreset() == null && allPresets.isNotEmpty()) {
                presetsAdapter.updatePresets(allPresets.values.toList())
            }
        } else {
            btnPaintMode.setBackgroundColor(Color.TRANSPARENT)
            btnPaintMode.setTextColor(primaryColor)
            btnPaintMode.iconTint = ColorStateList.valueOf(primaryColor)
            btnPaintMode.strokeColor = ColorStateList.valueOf(primaryColor)
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<FloatingActionButton>(R.id.floating_action_button)?.hide()
    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<FloatingActionButton>(R.id.floating_action_button)?.show()
    }

    private fun observeData() {
        val dao = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
        lifecycleScope.launch {
            combine(
                dao.getAllPresets(),
                dao.getActiveRule(),
                dao.getAllOverrides()
            ) { presets, rule, overrides ->
                Triple(presets, rule, overrides)
            }.collect { (presets, rule, overrides) ->
                allPresets = presets.associateBy { it.id }
                activeRule = rule
                allOverrides = overrides.associateBy({ it.julianDay }, { it.presetId }).toMutableMap()
                presetsAdapter.updatePresets(presets)
                if (rule != null) anchorJulianDay = rule.anchorJulianDay
                updateGridSelection()
            }
        }
    }

    private fun setupPresets() {
        presetsAdapter = ShiftPresetsAdapter(emptyList(), { _ -> }, { preset -> showPresetDialog(preset) })
        presetsRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        presetsRecycler.adapter = presetsAdapter
    }

    private fun showPresetDialog(preset: ShiftPreset?) {
        ShiftPresetDialogFragment.newInstance(preset).show(parentFragmentManager, "preset_dialog")
    }

    private fun showWizardDialog() {
        ShiftRuleWizardDialogFragment().show(parentFragmentManager, "wizard_dialog")
    }

    private fun setupCalendarSpinner() {
        val queryService = object : AsyncQueryService(requireContext()) {
            override fun onQueryComplete(token: Int, cookie: Any?, cursor: Cursor?) {
                cursor?.let {
                    val calendars = mutableListOf<Pair<Long, String>>()
                    while (it.moveToNext()) {
                        val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
                        val nameIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                        if (idIdx != -1 && nameIdx != -1) {
                            calendars.add(it.getLong(idIdx) to it.getString(nameIdx))
                        }
                    }
                    it.close()
                    if (calendars.isNotEmpty()) {
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, calendars.map { it.second })
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        calendarSpinner.adapter = adapter
                        calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                                selectedCalendarId = calendars[pos].first
                            }
                            override fun onNothingSelected(p: AdapterView<*>?) {}
                        }
                        selectedCalendarId = calendars[0].first
                    }
                }
            }
        }
        queryService.startQuery(0, null, CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "(${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR})",
            null, null)
    }

    private fun handlePaintTap(julianDay: Int) {
        val preset = presetsAdapter.getSelectedPreset()
        if (preset == null) {
            return
        }

        val presetId = preset.id
        if (allOverrides[julianDay] != presetId) {
            allOverrides[julianDay] = presetId
            updateGridSelection()
            val dao = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
            lifecycleScope.launch {
                dao.insertOverride(ShiftOverride(julianDay, presetId))
            }
        }
    }

    private fun updateGridSelection() {
        val firstDayOfWeek = com.android.calendar.Utils.getFirstDayOfWeekAsCalendar(requireContext())
        val days = getDaysForMonth(
            currentDisplayedMonth.get(Calendar.YEAR),
            currentDisplayedMonth.get(Calendar.MONTH),
            firstDayOfWeek
        )

        val startJd = getJulianDayForCalendar(days.first())
        val endJd = getJulianDayForCalendar(days.last())

        val shifts = ShiftRotationEngine.generateShiftsForRange(startJd - 10, endJd + 10, activeRule, allPresets, allOverrides)

        val todayTime = Time()
        todayTime.set(System.currentTimeMillis())
        val todayJd = Time.getJulianDay(todayTime.toMillis(), todayTime.getGmtOffset())

        val cells = days.map { cal ->
            val jd = getJulianDayForCalendar(cal)
            DayCell(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                julianDay = jd,
                isCurrentMonth = cal.get(Calendar.MONTH) == currentDisplayedMonth.get(Calendar.MONTH),
                isToday = jd == todayJd,
                isSelected = jd == selectedJulianDay,
                preset = shifts[jd],
                calendar = cal
            )
        }

        calendarAdapter.setDays(cells)
    }

    private fun getJulianDayForCalendar(cal: Calendar): Int {
        val time = Time()
        time.set(cal.timeInMillis)
        return Time.getJulianDay(time.toMillis(), time.getGmtOffset())
    }

    private fun clearOverrides() {
        lifecycleScope.launch {
            ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().clearAllOverrides()
        }
    }

    private fun saveShiftsToCalendar() {
        if (selectedCalendarId == -1L) return
        val shifts = ShiftRotationEngine.generateShiftsForRange(anchorJulianDay, anchorJulianDay + 365, activeRule, allPresets, allOverrides)
        val groups = shifts.entries.groupBy({ it.value }, { it.key })
        var pending = groups.size
        if (pending == 0) return
        groups.forEach { (preset, days) ->
            ShiftEventBuilder.saveShifts(requireContext(), selectedCalendarId, preset, days.toSet()) {
                pending--
                if (pending == 0) Toast.makeText(requireContext(), R.string.shift_save_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
