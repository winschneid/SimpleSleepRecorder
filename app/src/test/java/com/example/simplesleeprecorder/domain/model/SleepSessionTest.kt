package com.example.simplesleeprecorder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepSessionTest {

    private val hour = 3_600_000L

    /** Builds contiguous stage records starting at [startTime] with the given (stage, duration) pairs. */
    private fun session(
        startTime: Long = 0L,
        sleepOnsetTime: Long? = null,
        vararg stages: Pair<SleepStageType, Long>,
    ): SleepSession {
        var t = startTime
        val records = stages.map { (stage, duration) ->
            SleepStageRecord(sessionId = 0, stageType = stage, startTime = t, endTime = t + duration)
                .also { t += duration }
        }
        return SleepSession(
            startTime = startTime,
            endTime = t,
            alarmTime = t,
            sleepOnsetTime = sleepOnsetTime,
            audioUri = null,
            stageRecords = records,
        )
    }

    @Test
    fun `counts only awake periods sandwiched between sleep`() {
        val s = session(
            stages = arrayOf(
                SleepStageType.AWAKE to hour / 2, // falling asleep — not an awakening
                SleepStageType.LIGHT to 2 * hour,
                SleepStageType.AWAKE to hour / 4, // mid-sleep awakening
                SleepStageType.DEEP to 3 * hour,
                SleepStageType.AWAKE to hour / 4, // mid-sleep awakening
                SleepStageType.LIGHT to hour,
                SleepStageType.AWAKE to hour / 2, // final wake-up — not an awakening
            ),
        )
        assertEquals(2, s.awakeningsCount)
        assertEquals(hour / 2, s.midSleepAwakeMs)
    }

    @Test
    fun `no awakenings when sleep is uninterrupted`() {
        val s = session(
            stages = arrayOf(
                SleepStageType.AWAKE to hour / 2,
                SleepStageType.DEEP to 7 * hour,
                SleepStageType.AWAKE to hour / 4,
            ),
        )
        assertEquals(0, s.awakeningsCount)
        assertEquals(0L, s.midSleepAwakeMs)
    }

    @Test
    fun `sleep efficiency is sleep time over time in bed`() {
        val s = session(
            stages = arrayOf(
                SleepStageType.AWAKE to hour,
                SleepStageType.DEEP to 6 * hour,
                SleepStageType.AWAKE to hour,
            ),
        )
        assertEquals(8 * hour, s.timeInBedMs)
        assertEquals(75, s.sleepEfficiencyPercent) // 6h asleep of 8h in bed
    }

    @Test
    fun `score with detected onset uses the full formula`() {
        // 7h sleep, 20% deep, onset after 10 min -> all components maxed.
        val s = session(
            sleepOnsetTime = 10 * 60_000L,
            stages = arrayOf(
                SleepStageType.AWAKE to 10 * 60_000L,
                SleepStageType.LIGHT to (7 * hour * 80 / 100),
                SleepStageType.DEEP to (7 * hour * 20 / 100),
            ),
        )
        assertEquals(100, s.sleepScore)
    }

    @Test
    fun `score without onset is graded on remaining components`() {
        // Identical sleep, but onset undetected: previously capped at 75.
        val s = session(
            sleepOnsetTime = null,
            stages = arrayOf(
                SleepStageType.AWAKE to 10 * 60_000L,
                SleepStageType.LIGHT to (7 * hour * 80 / 100),
                SleepStageType.DEEP to (7 * hour * 20 / 100),
            ),
        )
        assertEquals(100, s.sleepScore)
    }

    @Test
    fun `score is zero with no sleep`() {
        val s = session(stages = arrayOf(SleepStageType.AWAKE to 8 * hour))
        assertEquals(0, s.sleepScore)
    }
}
