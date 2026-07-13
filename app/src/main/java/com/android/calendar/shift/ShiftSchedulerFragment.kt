package com.android.calendar.shift

import android.database.Cursor
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
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
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftOverride
import com.android.calendar.shift.db.ShiftPreset
import com.android.calendar.shift.db.ShiftRotationRule
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ws.xsoh.etar.R
import java.util.*

class ShiftSchedulerFragment : Fragment() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var presetsRecycler: RecyclerView
    private lateinit var presetsAdapter: ShiftPresetsAdapter
    private lateinit var monthFragment: ShiftMonthGridFragment

    private var selectedCalendarId: Long = -1
    private var anchorJulianDay: Int = 0

    private var allPresets: Map<Long, ShiftPreset> = emptyMap()
    private var activeRule: ShiftRotationRule? = null
    private var allOverrides: MutableMap<Int, Long> = mutableMapOf()

    private var paintModeEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = System.currentTimeMillis()
        val t = Time()
        t.set(now)
        anchorJulianDay = Time.getJulianDay(now, t.getGmtOffset())

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

        val paintModeSwitch = view.findViewById<SwitchMaterial>(R.id.paint_mode_switch)
        paintModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.e("ShiftDebug", "FRAGMENT: Paint Mode Toggled to $isChecked")
            Toast.makeText(context, "Paint Mode: " + if (isChecked) "ON" else "OFF", Toast.LENGTH_SHORT).show()
            paintModeEnabled = isChecked
            monthFragment.setPaintMode(isChecked)
        }

        view.findViewById<Button>(R.id.btn_add_preset).setOnClickListener { showPresetDialog(null) }
        view.findViewById<Button>(R.id.btn_clear).setOnClickListener { clearOverrides() }
        view.findViewById<Button>(R.id.btn_save).setOnClickListener { saveShiftsToCalendar() }

        val btnPickStartDate = view.findViewById<Button>(R.id.btn_pick_start_date)
        val time = Time(); time.setJulianDay(anchorJulianDay); btnPickStartDate.text = String.format("%04d-%02d-%02d", time.year, time.month + 1, time.day)
        btnPickStartDate.setOnClickListener { pickStartDate(btnPickStartDate) }

        view.findViewById<Button>(R.id.btn_auto_fill).setOnClickListener { showWizardDialog() }
        view.findViewById<Button>(R.id.btn_custom_rule).setOnClickListener { showRotationTemplateDialog() }

        setupPresets()
        setupCalendarSpinner()
        setupMonthGrid()
        observeData()

        return view
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
                Log.e("ShiftDebug", "FRAGMENT: Data Collected, overrides count=${overrides.size}")
                allPresets = presets.associateBy { it.id }
                activeRule = rule
                allOverrides = overrides.associateBy({ it.julianDay }, { it.presetId }).toMutableMap()
                presetsAdapter.updatePresets(presets)

                if (rule != null) {
                    anchorJulianDay = rule.anchorJulianDay
                }
                updateGridSelection()
            }
        }
    }

    private fun setupPresets() {
        presetsAdapter = ShiftPresetsAdapter(emptyList(), { _ -> }, { preset -> showPresetDialog(preset) })
        presetsRecycler.layoutManager = LinearLayoutManager(requireContext())
        presetsRecycler.adapter = presetsAdapter
    }

    private fun showPresetDialog(preset: ShiftPreset?) {
        ShiftPresetDialogFragment.newInstance(preset).show(parentFragmentManager, "preset_dialog")
    }

    private fun showRotationTemplateDialog() {
        ShiftRotationTemplateDialogFragment.newInstance(anchorJulianDay).show(parentFragmentManager, "rotation_template")
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
        monthFragment.onDayPaintedCallback = { julianDay ->
            handlePaintTap(julianDay)
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.month_view_container, monthFragment)
            .commit()
    }

    private fun handlePaintTap(julianDay: Int) {
        val preset = presetsAdapter.getSelectedPreset()
        val presetId = preset?.id ?: 0L
        Log.e("ShiftDebug", "FRAGMENT: handlePaintTap JD=$julianDay, preset=${preset?.title ?: "REST"}")

        allOverrides[julianDay] = presetId
        updateGridSelection()

        val dao = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
        lifecycleScope.launch {
            dao.insertOverride(ShiftOverride(julianDay, presetId))
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

    private fun pickStartDate(btn: Button) {
        val datePicker = MaterialDatePicker.Builder.datePicker().build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH)
            val d = calendar.get(Calendar.DAY_OF_MONTH)

            val localT = Time()
            localT.set(d, m, y)
            anchorJulianDay = Time.getJulianDay(localT.toMillis(), localT.getGmtOffset())

            btn.text = String.format("%04d-%02d-%02d", y, m + 1, d)
            updateGridSelection()
        }
        datePicker.show(parentFragmentManager, "date_picker")
    }

    private fun saveShiftsToCalendar() {
        if (selectedCalendarId == -1L) {
             Toast.makeText(requireContext(), R.string.shift_select_calendar, Toast.LENGTH_SHORT).show()
             return
        }

        val shifts = ShiftRotationEngine.generateShiftsForRange(anchorJulianDay, anchorJulianDay + 365, activeRule, allPresets, allOverrides)
        val groups = shifts.entries.groupBy({ it.value }, { it.key })

        var pending = groups.size
        if (pending == 0) {
            Toast.makeText(requireContext(), "No shifts to save", Toast.LENGTH_SHORT).show()
            return
        }

        groups.forEach { (preset, days) ->
            ShiftEventBuilder.saveShifts(requireContext(), selectedCalendarId, preset, days.toSet()) {
                pending--
                if (pending == 0) {
                    Toast.makeText(requireContext(), R.string.shift_save_success, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    class ShiftMonthGridFragment : MonthByWeekFragment() {
        var onDayPaintedCallback: ((Int) -> Unit)? = null
        private var paintModeEnabledInternal = false
        private var lastShifts: Map<Int, ShiftPreset>? = null

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
                    onDayPaintedListener = { julianDay -> onDayPaintedCallback?.invoke(julianDay) }
                    registerDataSetObserver(mObserver)
                }
            } else {
                mAdapter.updateParams(weekParams)
                (mAdapter as? ShiftMonthByWeekAdapter)?.onDayPaintedListener = onDayPaintedCallback
            }

            (mAdapter as? ShiftMonthByWeekAdapter)?.paintModeEnabled = paintModeEnabledInternal

            lastShifts?.let { updateSelection(it) }
            mAdapter.notifyDataSetChanged()
        }

        fun setPaintMode(enabled: Boolean) {
            Log.e("ShiftDebug", "GRID_FRAGMENT: Setting paint mode to $enabled")
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
