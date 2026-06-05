package com.example.simplesleeprecorder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_stage_records",
    foreignKeys = [ForeignKey(
        entity = SleepSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class SleepStageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val stageType: String,
    val startTime: Long,
    val endTime: Long,
)
