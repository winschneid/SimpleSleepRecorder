package com.example.simplesleeprecorder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SleepSessionEntity::class, SleepStageRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao

    companion object {
        @Volatile private var instance: SleepDatabase? = null

        fun getDatabase(context: Context): SleepDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SleepDatabase::class.java,
                    "sleep_database",
                ).build().also { instance = it }
            }
    }
}
