package com.example.simplesleeprecorder.domain.model

data class SleepStageRecord(
    val id: Long = 0,
    val sessionId: Long,
    val stageType: SleepStageType,
    val startTime: Long,
    val endTime: Long,
) {
    val durationMs: Long get() = endTime - startTime
    val durationMinutes: Long get() = durationMs / 60_000L
}
