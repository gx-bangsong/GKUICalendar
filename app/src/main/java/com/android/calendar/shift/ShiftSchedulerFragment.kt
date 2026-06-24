package com.android.calendar.shift

import android.database.Cursor
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.AsyncQueryService
import com.android.calendar.calendarcommon2.Time
import com.android.calendar.month.MonthByWeekFragment
import com.android.calendar.month.SimpleWeeksAdapter
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftPreset
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ws.xsoh.etar.R
import java.util.*

class ShiftSchedulerFragment : Fragment() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var presetsRecycler: RecyclerView
    private lateinit var presetsAdapter: ShiftPresetsAdapter
    private lateinit var monthFragment: ShiftMonthGridFragment
    private val selectedDaysMap = mutableMapOf<Int, ShiftPreset>()
    private var selectedCalendarId: Long = -1

    private lateinit var daysOnEdit: TextInputEditText
    private lateinit var daysOffEdit: TextInputEditText
    private var anchorJulianDay: Int = Time.getJulianDay(System.currentTimeMillis(), 0)
    private var customPattern: List<ShiftPreset?>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shift_scheduler, container, false)

        calendarSpinner = view.findViewById(R.id.calendar_spinner)
        presetsRecycler = view.findViewById(R.id.presets_recycler)
        daysOnEdit = view.findViewById(R.id.days_on_edit)
        daysOffEdit = view.findViewById(R.id.days_off_edit)

        val paintModeSwitch = view.findViewById<SwitchMaterial>(R.id.paint_mode_switch)

        view.findViewById<Button>(R.id.btn_add_preset).setOnClickListener { showPresetDialog(null) }
        view.findViewById<Button>(R.id.btn_clear).setOnClickListener {
            selectedDaysMap.clear()
            updateGridSelection()
        }
        view.findViewById<Button>(R.id.btn_save).setOnClickListener { saveShifts() }
        view.findViewById<Button>(R.id.btn_pick_start_date).setOnClickListener { pickStartDate() }
        view.findViewById<Button>(R.id.btn_auto_fill).setOnClickListener { autoFillPattern() }
        view.findViewById<Button>(R.id.btn_custom_rule).setOnClickListener { showRotationTemplateDialog() }

        setupPresets()
        setupCalendarSpinner()
        setupMonthGrid(paintModeSwitch)

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

    private fun setupPresets() {
        presetsAdapter = ShiftPresetsAdapter(emptyList(), { _ -> }, { preset -> showPresetDialog(preset) })
        presetsRecycler.layoutManager = LinearLayoutManager(requireContext())
        presetsRecycler.adapter = presetsAdapter

        lifecycleScope.launch {
            ShiftDatabase.getDatabase(requireContext()).shiftPresetDao().getAllPresets().collectLatest {
                presetsAdapter.updatePresets(it)
            }
        }
    }

    private fun showPresetDialog(preset: ShiftPreset?) {
        val dialog = ShiftPresetDialogFragment.newInstance(preset)
        dialog.listener = object : ShiftPresetDialogFragment.OnPresetSavedListener {
            override fun onPresetSaved() {}
        }
        dialog.show(parentFragmentManager, "preset_dialog")
    }

    private fun showRotationTemplateDialog() {
        val dialog = ShiftRotationTemplateDialogFragment()
        dialog.onPatternConfirmed = { pattern ->
            customPattern = pattern
            Toast.makeText(requireContext(), R.string.shift_custom, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "rotation_template")
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
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, calendars.map { it.second })
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    calendarSpinner.adapter = adapter
                    calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            selectedCalendarId = calendars[pos].first
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                }
            }
        }
        queryService.startQuery(0, null, CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            null, null, null)
    }

    private fun setupMonthGrid(paintModeSwitch: SwitchMaterial) {
        monthFragment = ShiftMonthGridFragment()
        monthFragment.onDayTappedCallback = { julianDay ->
            val preset = presetsAdapter.getSelectedPreset()
            if (preset != null) {
                if (selectedDaysMap.containsKey(julianDay)) {
                    selectedDaysMap.remove(julianDay)
                } else {
                    selectedDaysMap[julianDay] = preset
                }
                updateGridSelection()
            } else {
                Toast.makeText(requireContext(), R.string.shift_select_preset, Toast.LENGTH_SHORT).show()
            }
        }

        monthFragment.viewLifecycleOwnerLiveData.observe(viewLifecycleOwner) { owner ->
            if (owner != null) {
                monthFragment.getCalendarListView()?.setOnTouchListener { _, _ ->
                    paintModeSwitch.isChecked
                }
            }
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.month_view_container, monthFragment)
            .commit()
    }

    private fun updateGridSelection() {
        monthFragment.updateSelection(selectedDaysMap)
    }

    private fun pickStartDate() {
        val datePicker = MaterialDatePicker.Builder.datePicker().build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            anchorJulianDay = Time.getJulianDay(selection, 0)
        }
        datePicker.show(parentFragmentManager, "date_picker")
    }

    private fun autoFillPattern() {
        if (customPattern != null) {
            val days = ShiftRotationEngine.generateFromPattern(anchorJulianDay, customPattern!!.size)
            for (i in days.indices) {
                val jd = days[i]
                val preset = customPattern!![i % customPattern!!.size]
                if (preset != null) {
                    selectedDaysMap[jd] = preset
                } else {
                    selectedDaysMap.remove(jd)
                }
            }
        } else {
            val preset = presetsAdapter.getSelectedPreset()
            if (preset == null) {
                Toast.makeText(requireContext(), R.string.shift_select_preset, Toast.LENGTH_SHORT).show()
                return
            }
            val daysOn = daysOnEdit.text.toString().toIntOrNull() ?: 4
            val daysOff = daysOffEdit.text.toString().toIntOrNull() ?: 2
            val pattern = ShiftRotationEngine.generatePattern(anchorJulianDay, daysOn, daysOff)
            for (jd in pattern) {
                selectedDaysMap[jd] = preset
            }
        }
        updateGridSelection()
    }

    private fun saveShifts() {
        if (selectedDaysMap.isEmpty()) {
            Toast.makeText(requireContext(), R.string.shift_no_dates, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedCalendarId == -1L) {
             Toast.makeText(requireContext(), R.string.shift_select_calendar, Toast.LENGTH_SHORT).show()
             return
        }

        val groups = selectedDaysMap.entries.groupBy({ it.value }, { it.key })
        var pending = groups.size
        groups.forEach { (preset, days) ->
            ShiftEventBuilder.saveShifts(requireContext(), selectedCalendarId, preset, days.toSet()) {
                pending--
                if (pending == 0) {
                    Toast.makeText(requireContext(), R.string.shift_save_success, Toast.LENGTH_SHORT).show()
                    selectedDaysMap.clear()
                    updateGridSelection()
                }
            }
        }
    }

    class ShiftMonthGridFragment : MonthByWeekFragment() {
        var onDayTappedCallback: ((Int) -> Unit)? = null

        fun getCalendarListView() = mListView

        override fun setUpAdapter() {
            val weekParams = HashMap<String, Int>()
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_FOCUS_MONTH] = mCurrentMonthDisplayed
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_SHOW_WEEK] = if (mShowWeekNumber) 1 else 0
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_WEEK_START] = mFirstDayOfWeek
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_JULIAN_DAY] = Time.getJulianDay(mSelectedDay.toMillis(), 0)
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_DAYS_PER_WEEK] = mDaysPerWeek
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_NUM_WEEKS] = mNumWeeks

            if (mAdapter == null) {
                mAdapter = ShiftMonthByWeekAdapter(requireContext(), weekParams, mHandler).apply {
                    onDayTappedListener = { julianDay -> onDayTappedCallback?.invoke(julianDay) }
                    registerDataSetObserver(mObserver)
                }
            } else {
                mAdapter.updateParams(weekParams)
            }
            mAdapter.notifyDataSetChanged()
        }

        fun updateSelection(selectedDays: Map<Int, ShiftPreset>) {
            val colors = selectedDays.mapValues { it.value.color }
            (mAdapter as? ShiftMonthByWeekAdapter)?.setSelectedDays(colors)
        }
    }
}
