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

    val sleepScore: Int
        get() {
            if (totalSleepMs <= 0L) return 0
            // Deep sleep ratio: 20% of total sleep = full 40 pts
            val deepScore = (deepMs.toFloat() / totalSleepMs / 0.20f).coerceAtMost(1f) * 40f
            // Duration: 7 hours = full 35 pts
            val durationScore = (totalSleepMs / (7 * 3_600_000f)).coerceAtMost(1f) * 35f
            // Sleep onset: ≤15 min = full 25 pts, ≥30 min = 0 pts
            val onsetScore = sleepOnsetMs?.let { ms ->
                ((30f - ms / 60_000f) / 15f).coerceIn(0f, 1f) * 25f
            } ?: 0f
            return (deepScore + durationScore + onsetScore).toInt().coerceIn(0, 100)
        }
}
