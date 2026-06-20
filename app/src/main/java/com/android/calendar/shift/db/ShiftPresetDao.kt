package com.android.calendar.shift.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftPresetDao {
    @Query("SELECT * FROM shift_presets")
    fun getAllPresets(): Flow<List<ShiftPreset>>

    @Insert
    suspend fun insert(preset: ShiftPreset): Long

    @Update
    suspend fun update(preset: ShiftPreset)

    @Delete
    suspend fun delete(preset: ShiftPreset)
}
