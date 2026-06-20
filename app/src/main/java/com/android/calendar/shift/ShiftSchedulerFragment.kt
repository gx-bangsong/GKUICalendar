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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ws.xsoh.etar.R
import java.util.*

class ShiftSchedulerFragment : Fragment() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var presetsRecycler: RecyclerView
    private lateinit var presetsAdapter: ShiftPresetsAdapter
    private lateinit var monthFragment: ShiftMonthGridFragment
    private val selectedJulianDays = HashSet<Int>()
    private var selectedCalendarId: Long = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shift_scheduler, container, false)

        calendarSpinner = view.findViewById(R.id.calendar_spinner)
        presetsRecycler = view.findViewById(R.id.presets_recycler)

        view.findViewById<Button>(R.id.btn_add_preset).setOnClickListener {
            showPresetDialog(null)
        }

        view.findViewById<Button>(R.id.btn_clear).setOnClickListener {
            selectedJulianDays.clear()
            updateGridSelection()
        }

        view.findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveShifts()
        }

        setupPresets()
        setupCalendarSpinner()
        setupMonthGrid()

        return view
    }

    private fun setupPresets() {
        presetsAdapter = ShiftPresetsAdapter(emptyList(), { preset ->
            updateGridSelection()
        }, { preset ->
            showPresetDialog(preset)
        })
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
            override fun onPresetSaved() {
                // Flow will auto-update
            }
        }
        dialog.show(parentFragmentManager, "preset_dialog")
    }

    private fun setupCalendarSpinner() {
        val queryService = object : AsyncQueryService(requireContext()) {
            override fun onQueryComplete(token: Int, cookie: Any?, cursor: Cursor?) {
                cursor?.let {
                    val calendars = mutableListOf<Pair<Long, String>>()
                    while (it.moveToNext()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                        val name = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                        calendars.add(id to name)
                    }
                    it.close()

                    val context = context ?: return
                    val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, calendars.map { it.second })
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    calendarSpinner.adapter = adapter

                    calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            selectedCalendarId = calendars[position].first
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }
            }
        }
        queryService.startQuery(0, null, CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            null, null, null)
    }

    private fun setupMonthGrid() {
        monthFragment = ShiftMonthGridFragment()
        monthFragment.onDayTappedCallback = { julianDay ->
            if (selectedJulianDays.contains(julianDay)) {
                selectedJulianDays.remove(julianDay)
            } else {
                selectedJulianDays.add(julianDay)
            }
            updateGridSelection()
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.month_view_container, monthFragment)
            .commit()
    }

    private fun updateGridSelection() {
        val preset = presetsAdapter.getSelectedPreset()
        monthFragment.updateSelection(selectedJulianDays, preset?.color ?: 0x660000FF.toInt())
    }

    private fun saveShifts() {
        val preset = presetsAdapter.getSelectedPreset()
        if (preset == null) {
            Toast.makeText(requireContext(), R.string.select_preset, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedJulianDays.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_dates_selected, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedCalendarId == -1L) {
             Toast.makeText(requireContext(), R.string.select_calendar, Toast.LENGTH_SHORT).show()
             return
        }

        ShiftEventBuilder.saveShifts(requireContext(), selectedCalendarId, preset, selectedJulianDays) {
            Toast.makeText(requireContext(), R.string.shift_save_success, Toast.LENGTH_SHORT).show()
            selectedJulianDays.clear()
            updateGridSelection()
        }
    }

    class ShiftMonthGridFragment : MonthByWeekFragment() {
        var onDayTappedCallback: ((Int) -> Unit)? = null

        override fun setUpAdapter() {
            val weekParams = HashMap<String, Int>()
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_FOCUS_MONTH] = mCurrentMonthDisplayed
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_SHOW_WEEK] = if (mShowWeekNumber) 1 else 0
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_WEEK_START] = mFirstDayOfWeek
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_JULIAN_DAY] = Time.getJulianDay(mSelectedDay.toMillis(), 0)
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_DAYS_PER_WEEK] = mDaysPerWeek
            weekParams[SimpleWeeksAdapter.WEEK_PARAMS_NUM_WEEKS] = mNumWeeks

            val context = context ?: return
            if (mAdapter == null) {
                val adapter = ShiftMonthByWeekAdapter(context, weekParams, mHandler)
                adapter.onDayTappedListener = { julianDay ->
                    onDayTappedCallback?.invoke(julianDay)
                }
                mAdapter = adapter
                mAdapter.registerDataSetObserver(mObserver)
            } else {
                mAdapter.updateParams(weekParams)
            }
            mAdapter.notifyDataSetChanged()
        }

        fun updateSelection(days: Set<Int>, color: Int) {
            (mAdapter as? ShiftMonthByWeekAdapter)?.setSelectedDays(days, color)
        }
    }
}
