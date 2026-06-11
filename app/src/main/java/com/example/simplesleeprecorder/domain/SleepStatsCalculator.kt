package com.example.simplesleeprecorder.domain

import com.example.simplesleeprecorder.domain.model.SleepSession
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Aggregates recent sessions into history-screen statistics. Pure logic,
 * kept out of the UI layer for unit testing.
 *
 * Days are "sleep days" ([SleepSession.sleepDateMillis]): a session started
 * before 6:00 counts toward the previous calendar day.
 */
object SleepStatsCalculator {

    data class DailySleep(
        /** Local midnight of the sleep day. */
        val dayStartMillis: Long,
        val totalSleepMs: Long,
    )

    data class Stats(
        val sessionCount: Int,
        val averageSleepMs: Long,
        val averageScore: Int,
        /** Average over sessions where onset was detected; null if none. */
        val averageOnsetMs: Long?,
        /** One entry per day in the range, oldest first; days without data are zero. */
        val dailySleep: List<DailySleep>,
    )

    /**
     * Stats over the [days] sleep-days ending at [nowMillis]'s sleep day.
     * Returns null when no session falls in the range.
     */
    fun forLastDays(
        sessions: List<SleepSession>,
        days: Int = 7,
        nowMillis: Long = System.currentTimeMillis(),
    ): Stats? {
        val todayStart = dayStart(nowMillis - SleepSession.DAY_BOUNDARY_HOUR * 3_600_000L)
        // Walk back via Calendar so DST shifts don't skew the buckets.
        val dayStarts = (days - 1 downTo 0).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, -offset)
            }.timeInMillis
        }

        val byDay = sessions
            .groupBy { dayStart(it.sleepDateMillis) }
            .filterKeys { it >= dayStarts.first() && it <= todayStart }
        val inRange = byDay.values.flatten()
        if (inRange.isEmpty()) return null

        val onsets = inRange.mapNotNull { it.sleepOnsetMs }
        return Stats(
            sessionCount = inRange.size,
            averageSleepMs = inRange.map { it.totalSleepMs }.average().toLong(),
            averageScore = inRange.map { it.sleepScore }.average().roundToInt(),
            averageOnsetMs = if (onsets.isEmpty()) null else onsets.average().toLong(),
            dailySleep = dayStarts.map { day ->
                DailySleep(day, byDay[day]?.sumOf { it.totalSleepMs } ?: 0L)
            },
        )
    }

    private fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
