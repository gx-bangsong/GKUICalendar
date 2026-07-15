package com.android.calendar.shift

import android.database.Cursor
import android.graphics.Rect
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.AsyncQueryService
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.month.MonthByWeekFragment
import com.android.calendar.month.SimpleWeeksAdapter
import com.android.calendar.month.SimpleWeekView
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftOverride
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ws.xsoh.etar.R
import java.util.*

class ShiftSchedulerFragment : Fragment() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var presetsRecycler: RecyclerView
    private lateinit var presetsAdapter: ShiftPresetsAdapter
    private lateinit var monthFragment: ShiftMonthGridFragment
    private lateinit var paintFab: ExtendedFloatingActionButton
    private lateinit var touchOverlay: ShiftTouchOverlay

    private var selectedCalendarId: Long = -1
    private var anchorJulianDay: Int = 0

    private var allPresets: Map<Long, ShiftPreset> = emptyMap()
    private var activeRule: ShiftRotationRule? = null
    private var allOverrides: MutableMap<Int, Long> = mutableMapOf()

    private var paintModeEnabled = false
    private var lastPaintedJd = -1
    private var lastToastTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = Time()
        t.set(System.currentTimeMillis())
        anchorJulianDay = Time.getJulianDay(t.toMillis(), t.getGmtOffset())

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shift_scheduler, container, false)

        calendarSpinner = view.findViewById(R.id.calendar_spinner)
        presetsRecycler = view.findViewById(R.id.presets_recycler)
        paintFab = view.findViewById(R.id.paint_mode_fab)
        touchOverlay = view.findViewById(R.id.paint_touch_overlay)

        paintFab.setOnClickListener { togglePaintMode(!paintModeEnabled) }

        touchOverlay.onTouchMoving = { rawX, rawY ->
            if (paintModeEnabled) {
                processPaintAt(rawX, rawY)
            }
        }
        touchOverlay.onTouchStopped = {
            lastPaintedJd = -1

        }

        view.findViewById<Button>(R.id.btn_add_preset).setOnClickListener { showPresetDialog(null) }
        view.findViewById<Button>(R.id.btn_clear).setOnClickListener { clearOverrides() }
        view.findViewById<Button>(R.id.btn_save).setOnClickListener { saveShiftsToCalendar() }
        view.findViewById<Button>(R.id.btn_auto_fill).setOnClickListener { showWizardDialog() }

        setupPresets()
        setupCalendarSpinner()
        setupMonthGrid()
        observeData()

        return view
    }


    private fun processPaintAt(rawX: Float, rawY: Float) {
        val listView = monthFragment.getCalendarListView() ?: return
        for (i in 0 until listView.childCount) {
            val child = listView.getChildAt(i)
            val rect = Rect()
            child.getGlobalVisibleRect(rect)
            if (rect.contains(rawX.toInt(), rawY.toInt())) {
                if (child is SimpleWeekView) {
                    val touchXInChild = rawX - rect.left
                    val time = child.getDayFromLocation(touchXInChild)
                    if (time != null) {
                        val jd = Time.getJulianDay(time.toMillis(), time.getGmtOffset())
                        if (jd != lastPaintedJd) {
                            lastPaintedJd = jd

                            try {
                                child.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            } catch (e: Exception) {}
                            handlePaintTap(jd)
                        }
                    }
                }
                break
            }
        }
    }

    private fun togglePaintMode(enabled: Boolean) {
        paintModeEnabled = enabled
        touchOverlay.visibility = if (enabled) View.VISIBLE else View.GONE


        if (enabled) {
            paintFab.extend()
            paintFab.setBackgroundColor(0xFF3F51B5.toInt())
            paintFab.setTextColor(0xFFFFFFFF.toInt())
            paintFab.iconTint = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())

            if (presetsAdapter.getSelectedPreset() == null && allPresets.isNotEmpty()) {

                presetsAdapter.updatePresets(allPresets.values.toList())
            }
        } else {
            paintFab.shrink()
            paintFab.setBackgroundColor(0xFFEEEEEE.toInt())
            paintFab.setTextColor(0xFF000000.toInt())
            paintFab.iconTint = android.content.res.ColorStateList.valueOf(0xFF000000.toInt())
        }
        monthFragment.setPaintMode(enabled)
    }

    private fun showThrottledToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 2000) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            lastToastTime = now
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

    private fun setupMonthGrid() {
        monthFragment = ShiftMonthGridFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.month_view_container, monthFragment)
            .commit()
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
        if (!::monthFragment.isInitialized) return
        val startJd = anchorJulianDay - 60
        val endJd = anchorJulianDay + 400
        val shifts = ShiftRotationEngine.generateShiftsForRange(startJd, endJd, activeRule, allPresets, allOverrides)
        monthFragment.updateSelection(shifts)
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

    class ShiftMonthGridFragment : MonthByWeekFragment() {
        private var paintModeEnabledInternal = false
        private var lastShifts: Map<Int, ShiftPreset>? = null

        fun getCalendarListView(): ListView? = mListView

        override fun setUpAdapter() {
            val weekParams = HashMap<String, Int>()
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_FOCUS_MONTH] = mCurrentMonthDisplayed
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_SHOW_WEEK] = if (mShowWeekNumber) 1 else 0
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_WEEK_START] = mFirstDayOfWeek
            val localJd = Time.getJulianDay(mSelectedDay.toMillis(), mSelectedDay.getGmtOffset())
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_JULIAN_DAY] = localJd
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_DAYS_PER_WEEK] = mDaysPerWeek
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_NUM_WEEKS] = mNumWeeks

            if (mAdapter == null) {
                mAdapter = ShiftMonthByWeekAdapter(requireActivity(), weekParams, mHandler).apply {
                    registerDataSetObserver(mObserver)
                }
            } else {
                mAdapter.updateParams(weekParams)
            }
            (mAdapter as? ShiftMonthByWeekAdapter)?.paintModeEnabled = paintModeEnabledInternal
            lastShifts?.let { updateSelection(it) }
            mAdapter.notifyDataSetChanged()
        }

        fun setPaintMode(enabled: Boolean) {
            paintModeEnabledInternal = enabled
            (mAdapter as? ShiftMonthByWeekAdapter)?.paintModeEnabled = enabled
        }

        fun updateSelection(shifts: Map<Int, ShiftPreset>) {
            lastShifts = shifts
            if (mAdapter == null) return
            val colors = shifts.mapValues { it.value.color }
            (mAdapter as? ShiftMonthByWeekAdapter)?.setSelectedDays(colors)
        }
    }
}
