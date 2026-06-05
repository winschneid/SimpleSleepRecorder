package com.example.simplesleeprecorder.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class SleepSessionWithStages(
    @Embedded val session: SleepSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val stageRecords: List<SleepStageRecordEntity>,
)
