package com.android.calendar.shift.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftPresetDao {
    @Query("SELECT * FROM shift_presets")
    fun getAllPresets(): Flow<List<ShiftPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: ShiftPreset)

    @Delete
    suspend fun deletePreset(preset: ShiftPreset)

    @Query("SELECT * FROM shift_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): ShiftPreset?

    // Rotation Rule
    @Query("SELECT * FROM shift_rotation_rule WHERE id = 1")
    fun getActiveRule(): Flow<ShiftRotationRule?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateActiveRule(rule: ShiftRotationRule)

    @Query("DELETE FROM shift_rotation_rule WHERE id = 1")
    suspend fun clearActiveRule()

    // Overrides
    @Query("SELECT * FROM shift_overrides")
    fun getAllOverrides(): Flow<List<ShiftOverride>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: ShiftOverride)

    @Query("DELETE FROM shift_overrides WHERE julianDay = :julianDay")
    suspend fun removeOverride(julianDay: Int)

    @Query("DELETE FROM shift_overrides")
    suspend fun clearAllOverrides()
}
