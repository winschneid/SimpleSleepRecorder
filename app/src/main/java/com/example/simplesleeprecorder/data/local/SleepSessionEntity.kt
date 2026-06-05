package com.example.simplesleeprecorder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val alarmTime: Long,
    val sleepOnsetTime: Long?,
    val audioUri: String?,
)
