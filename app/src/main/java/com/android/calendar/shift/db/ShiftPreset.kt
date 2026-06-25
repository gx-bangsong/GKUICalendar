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

@Entity(tableName = "shift_rotation_rule")
data class ShiftRotationRule(
    @PrimaryKey val id: Int = 1,
    val anchorJulianDay: Int,
    val patternPresetIds: String // Comma-separated IDs, "0" for rest
)

@Entity(tableName = "shift_overrides")
data class ShiftOverride(
    @PrimaryKey val julianDay: Int,
    val presetId: Long // 0 for rest/delete
)
