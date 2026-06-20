package com.android.calendar.shift.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_presets")
data class ShiftPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Int, // Minutes from midnight
    val endTime: Int,   // Minutes from midnight
    val alarmOffset: Int, // Minutes before start
    val ignoreHoliday: Boolean,
    val color: Int
)
