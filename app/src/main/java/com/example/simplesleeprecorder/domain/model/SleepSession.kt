package com.example.simplesleeprecorder.domain.model

data class SleepSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val alarmTime: Long,
    val sleepOnsetTime: Long?,
    val audioUri: String?,
    val stageRecords: List<SleepStageRecord> = emptyList(),
) {
    val totalSleepMs: Long
        get() = stageRecords
            .filter { it.stageType != SleepStageType.AWAKE }
            .sumOf { it.durationMs }

    val dozingMs: Long
        get() = stageRecords.filter { it.stageType == SleepStageType.DOZING }.sumOf { it.durationMs }

    val lightMs: Long
        get() = stageRecords.filter { it.stageType == SleepStageType.LIGHT }.sumOf { it.durationMs }

    val deepMs: Long
        get() = stageRecords.filter { it.stageType == SleepStageType.DEEP }.sumOf { it.durationMs }

    val sleepOnsetMs: Long?
        get() = sleepOnsetTime?.let { it - startTime }
}
