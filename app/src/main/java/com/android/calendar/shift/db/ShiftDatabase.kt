package com.android.calendar.shift.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ShiftPreset::class], version = 1, exportSchema = false)
abstract class ShiftDatabase : RoomDatabase() {
    abstract fun shiftPresetDao(): ShiftPresetDao

    companion object {
        @Volatile
        private var INSTANCE: ShiftDatabase? = null

        fun getDatabase(context: Context): ShiftDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShiftDatabase::class.java,
                    "shift_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
