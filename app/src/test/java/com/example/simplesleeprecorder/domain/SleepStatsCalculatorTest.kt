package com.example.simplesleeprecorder.domain

import com.example.simplesleeprecorder.domain.model.SleepSession
import com.example.simplesleeprecorder.domain.model.SleepStageRecord
import com.example.simplesleeprecorder.domain.model.SleepStageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SleepStatsCalculatorTest {

    private val hour = 3_600_000L

    // Fixed reference: a local-time noon, so the 6:00 sleep-day boundary
    // never shifts sessions across days unexpectedly.
    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, 12, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** A session that started [daysAgo] days before [now] at 23:00 and slept [sleepHours]. */
    private fun sessionDaysAgo(
        daysAgo: Int,
        sleepHours: Long = 7,
        onsetMinutes: Long? = 10,
    ): SleepSession {
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val awakeMs = 30 * 60_000L
        val records = listOf(
            SleepStageRecord(0, 0, SleepStageType.AWAKE, start, start + awakeMs),
            SleepStageRecord(0, 0, SleepStageType.LIGHT, start + awakeMs, start + awakeMs + sleepHours * hour),
        )
        return SleepSession(
            startTime = start,
            endTime = start + awakeMs + sleepHours * hour,
            alarmTime = 0L,
            sleepOnsetTime = onsetMinutes?.let { start + it * 60_000L },
            audioUri = null,
            stageRecords = records,
        )
    }

    @Test
    fun `averages sessions within the window`() {
        val sessions = listOf(
            sessionDaysAgo(1, sleepHours = 6, onsetMinutes = 10),
            sessionDaysAgo(2, sleepHours = 8, onsetMinutes = 20),
        )
        val stats = SleepStatsCalculator.forLastDays(sessions, days = 7, nowMillis = now)!!
        assertEquals(2, stats.sessionCount)
        assertEquals(7 * hour, stats.averageSleepMs)
        assertEquals(15 * 60_000L, stats.averageOnsetMs)
    }

    @Test
    fun `onset average ignores sessions without onset and is null when none have it`() {
        val mixed = listOf(
            sessionDaysAgo(1, onsetMinutes = 10),
            sessionDaysAgo(2, onsetMinutes = null),
        )
        assertEquals(
            10 * 60_000L,
            SleepStatsCalculator.forLastDays(mixed, days = 7, nowMillis = now)!!.averageOnsetMs,
        )

        val noneDetected = listOf(sessionDaysAgo(1, onsetMinutes = null))
        assertNull(SleepStatsCalculator.forLastDays(noneDetected, days = 7, nowMillis = now)!!.averageOnsetMs)
    }

    @Test
    fun `sessions outside the window are excluded`() {
        val sessions = listOf(
            sessionDaysAgo(1, sleepHours = 6),
            sessionDaysAgo(10, sleepHours = 2), // outside 7-day window
        )
        val stats = SleepStatsCalculator.forLastDays(sessions, days = 7, nowMillis = now)!!
        assertEquals(1, stats.sessionCount)
        assertEquals(6 * hour, stats.averageSleepMs)
    }

    @Test
    fun `daily breakdown covers every day oldest first with zeros for missing days`() {
        val sessions = listOf(
            sessionDaysAgo(0, sleepHours = 7),
            sessionDaysAgo(3, sleepHours = 5),
        )
        val stats = SleepStatsCalculator.forLastDays(sessions, days = 7, nowMillis = now)!!
        assertEquals(7, stats.dailySleep.size)
        // Oldest first; entries are strictly increasing day starts.
        stats.dailySleep.zipWithNext { a, b -> assertTrue(a.dayStartMillis < b.dayStartMillis) }
        assertEquals(7 * hour, stats.dailySleep.last().totalSleepMs)
        assertEquals(5 * hour, stats.dailySleep[6 - 3].totalSleepMs)
        assertEquals(0L, stats.dailySleep.first().totalSleepMs)
    }

    @Test
    fun `early-morning session counts toward the previous sleep day`() {
        // Started at 2:00 today — before the 6:00 boundary, so it belongs to yesterday.
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val s = SleepSession(
            startTime = start,
            endTime = start + 6 * hour,
            alarmTime = 0L,
            sleepOnsetTime = null,
            audioUri = null,
            stageRecords = listOf(
                SleepStageRecord(0, 0, SleepStageType.LIGHT, start, start + 6 * hour),
            ),
        )
        val stats = SleepStatsCalculator.forLastDays(listOf(s), days = 7, nowMillis = now)!!
        // Lands in the second-to-last bucket (yesterday), not today.
        assertEquals(6 * hour, stats.dailySleep[5].totalSleepMs)
        assertEquals(0L, stats.dailySleep.last().totalSleepMs)
    }

    @Test
    fun `returns null when no session is in range`() {
        assertNull(SleepStatsCalculator.forLastDays(emptyList(), days = 7, nowMillis = now))
        assertNull(
            SleepStatsCalculator.forLastDays(
                listOf(sessionDaysAgo(30)),
                days = 7,
                nowMillis = now,
            ),
        )
    }
}
