package com.android.calendar.shift

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.calendar.colorpicker.ColorPickerDialog
import com.android.calendar.shift.db.ShiftDatabase
import com.android.calendar.shift.db.ShiftPreset
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import ws.xsoh.etar.R

class ShiftPresetDialogFragment : DialogFragment() {

    private var presetId: Long = 0
    private var startTime: Int = 480 // 08:00
    private var endTime: Int = 1020   // 17:00
    private var selectedColor: Int = -0x1000000 // Black default, will be updated

    interface OnPresetSavedListener {
        fun onPresetSaved()
    }

    var listener: OnPresetSavedListener? = null

    companion object {
        fun newInstance(preset: ShiftPreset? = null): ShiftPresetDialogFragment {
            val fragment = ShiftPresetDialogFragment()
            preset?.let {
                val args = Bundle()
                args.putLong("id", it.id)
                args.putString("title", it.title)
                args.putInt("startTime", it.startTime)
                args.putInt("endTime", it.endTime)
                args.putInt("alarmOffset", it.alarmOffset)
                args.putBoolean("ignoreHoliday", it.ignoreHoliday)
                args.putInt("color", it.color)
                fragment.arguments = args
            }
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_shift_preset, null)

        val titleEdit = view.findViewById<TextInputEditText>(R.id.shift_title_edit)
        val startTimeButton = view.findViewById<Button>(R.id.start_time_button)
        val endTimeButton = view.findViewById<Button>(R.id.end_time_button)
        val alarmOffsetEdit = view.findViewById<TextInputEditText>(R.id.alarm_offset_edit)
        val holidaySwitch = view.findViewById<SwitchMaterial>(R.id.ignore_holiday_switch)
        val colorPreview = view.findViewById<View>(R.id.color_preview)

        selectedColor = requireContext().getColor(R.color.colorPrimary)

        arguments?.let {
            presetId = it.getLong("id")
            titleEdit.setText(it.getString("title"))
            startTime = it.getInt("startTime")
            endTime = it.getInt("endTime")
            alarmOffsetEdit.setText(it.getInt("alarmOffset").toString())
            holidaySwitch.isChecked = it.getBoolean("ignoreHoliday")
            selectedColor = it.getInt("color")
        }

        updateTimeButtons(startTimeButton, endTimeButton)
        colorPreview.setBackgroundColor(selectedColor)

        startTimeButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                startTime = h * 60 + m
                updateTimeButtons(startTimeButton, endTimeButton)
            }, startTime / 60, startTime % 60, true).show()
        }

        endTimeButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                endTime = h * 60 + m
                updateTimeButtons(startTimeButton, endTimeButton)
            }, endTime / 60, endTime % 60, true).show()
        }

        colorPreview.setOnClickListener {
            val colors = intArrayOf(
                0xff1a73e8.toInt(), 0xffd50000.toInt(), 0xfff4511e.toInt(), 0xfff6bf26.toInt(),
                0xff33b679.toInt(), 0xff0b8043.toInt(), 0xff039be5.toInt(), 0xff3f51b5.toInt(),
                0xff7986cb.toInt(), 0xff8e24aa.toInt(), 0xff616161.toInt()
            )
            val dialog = ColorPickerDialog.newInstance(R.string.calendar_color_picker_dialog_title,
                colors, selectedColor, 4, ColorPickerDialog.SIZE_SMALL)
            dialog.setOnColorSelectedListener { color ->
                selectedColor = color
                colorPreview.setBackgroundColor(color)
            }
            dialog.show(parentFragmentManager, "color_picker")
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(if (presetId == 0L) R.string.add_shift_preset else R.string.edit_shift_preset)
            .setView(view)
            .setPositiveButton(R.string.save_shift) { _: DialogInterface, _: Int ->
                val title = titleEdit.text.toString()
                val offset = alarmOffsetEdit.text.toString().toIntOrNull() ?: 90
                val preset = ShiftPreset(presetId, title, startTime, endTime, offset, holidaySwitch.isChecked, selectedColor)
                savePreset(preset)
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (presetId != 0L) {
            builder.setNeutralButton(R.string.delete_preset) { _: DialogInterface, _: Int ->
                deletePreset()
            }
        }

        return builder.create()
    }

    private fun updateTimeButtons(start: Button, end: Button) {
        start.text = String.format("%s: %02d:%02d", getString(R.string.start_time), startTime / 60, startTime % 60)
        end.text = String.format("%s: %02d:%02d", getString(R.string.end_time), endTime / 60, endTime % 60)
    }

    private fun savePreset(preset: ShiftPreset) {
        lifecycleScope.launch {
            val dao = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
            if (preset.id == 0L) {
                dao.insert(preset)
            } else {
                dao.update(preset)
            }
            listener?.onPresetSaved()
        }
    }

    private fun deletePreset() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_preset)
            .setMessage(R.string.confirm_delete_preset)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                lifecycleScope.launch {
                    val dao = ShiftDatabase.getDatabase(requireContext()).shiftPresetDao()
                    dao.delete(ShiftPreset(presetId, "", 0, 0, 0, false, 0))
                    listener?.onPresetSaved()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
