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

    /**
     * 中途覚醒: 睡眠と睡眠に挟まれた AWAKE 区間。先頭（寝付くまで）と
     * 末尾（起床後）の AWAKE 区間は含まない。
     */
    val midSleepAwakeRecords: List<SleepStageRecord>
        get() = stageRecords.filterIndexed { index, record ->
            record.stageType == SleepStageType.AWAKE &&
                index > 0 && index < stageRecords.lastIndex
        }

    val awakeningsCount: Int
        get() = midSleepAwakeRecords.size

    val midSleepAwakeMs: Long
        get() = midSleepAwakeRecords.sumOf { it.durationMs }

    val timeInBedMs: Long
        get() = endTime - startTime

    /** 睡眠効率: 就床時間に対する睡眠時間の割合（%）。 */
    val sleepEfficiencyPercent: Int
        get() = if (timeInBedMs > 0) {
            (totalSleepMs * 100 / timeInBedMs).toInt().coerceIn(0, 100)
        } else 0

    /**
     * 「睡眠日」を表す時刻。6時より前に計測を開始した記録は前日扱いとなる。
     * 表示側で日付（yyyy/MM/dd）へ整形して使う。
     */
    val sleepDateMillis: Long
        get() = startTime - DAY_BOUNDARY_HOUR * 60 * 60 * 1000L

    companion object {
        /** 日付の境界となる時刻。これより前に計測を開始した記録は前日の記録として扱う。 */
        const val DAY_BOUNDARY_HOUR = 6
    }

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
            }
            // When onset wasn't detected, grade on the remaining components
            // instead of silently scoring the missing one as zero.
            val score = if (onsetScore != null) {
                deepScore + durationScore + onsetScore
            } else {
                (deepScore + durationScore) * 100f / 75f
            }
            return score.toInt().coerceIn(0, 100)
        }
}
