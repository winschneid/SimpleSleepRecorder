package com.example.simplesleeprecorder.domain

import com.example.simplesleeprecorder.domain.model.SleepStageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepStageAnalyzerTest {

    private val windowMs = 30_000L
    private val startTime = 1_000_000L

    /** Feeds [count] windows of [stdDev], returning the end time of the last one. */
    private fun SleepStageAnalyzer.feedWindows(
        count: Int,
        stdDev: Float,
        screenWasOn: Boolean = false,
        firstWindowEnd: Long = startTime + windowMs,
    ): Long {
        var end = firstWindowEnd
        repeat(count) {
            onWindow(stdDev, screenWasOn, end)
            end += windowMs
        }
        return end - windowMs
    }

    private fun newAnalyzer(onsetWindows: Int = SleepStageAnalyzer.ONSET_CONSECUTIVE_WINDOWS) =
        SleepStageAnalyzer(onsetWindows).apply { start(startTime) }

    @Test
    fun `classifies stage from movement stddev`() {
        val analyzer = newAnalyzer()
        analyzer.onWindow(stdDev = 3.0f, screenWasOn = false, windowEnd = startTime + windowMs)
        assertEquals(SleepStageType.AWAKE, analyzer.currentStage)
        analyzer.onWindow(stdDev = 1.0f, screenWasOn = false, windowEnd = startTime + 2 * windowMs)
        assertEquals(SleepStageType.DOZING, analyzer.currentStage)
        analyzer.onWindow(stdDev = 0.3f, screenWasOn = false, windowEnd = startTime + 3 * windowMs)
        assertEquals(SleepStageType.LIGHT, analyzer.currentStage)
        analyzer.onWindow(stdDev = 0.05f, screenWasOn = false, windowEnd = startTime + 4 * windowMs)
        assertEquals(SleepStageType.DEEP, analyzer.currentStage)
    }

    @Test
    fun `screen on forces awake regardless of stillness`() {
        val analyzer = newAnalyzer()
        analyzer.onWindow(stdDev = 0.01f, screenWasOn = true, windowEnd = startTime + windowMs)
        assertEquals(SleepStageType.AWAKE, analyzer.currentStage)
    }

    @Test
    fun `no onset before sustained stillness threshold`() {
        val analyzer = newAnalyzer(onsetWindows = 20)
        analyzer.feedWindows(19, stdDev = 0.05f)
        assertNull(analyzer.sleepOnsetTime)
    }

    @Test
    fun `onset is start of the sustained still run`() {
        val analyzer = newAnalyzer(onsetWindows = 20)
        // 5 windows awake (using the phone), then 20 still windows.
        val lastAwakeEnd = analyzer.feedWindows(5, stdDev = 3.0f)
        analyzer.feedWindows(20, stdDev = 0.05f, firstWindowEnd = lastAwakeEnd + windowMs)
        // Onset is where the still run began: the end of the last awake window.
        assertEquals(lastAwakeEnd, analyzer.sleepOnsetTime)
    }

    @Test
    fun `movement resets the still run`() {
        val analyzer = newAnalyzer(onsetWindows = 20)
        var end = analyzer.feedWindows(10, stdDev = 0.05f)
        end = analyzer.feedWindows(1, stdDev = 3.0f, firstWindowEnd = end + windowMs)
        val runStart = end
        end = analyzer.feedWindows(19, stdDev = 0.05f, firstWindowEnd = end + windowMs)
        assertNull(analyzer.sleepOnsetTime)
        analyzer.feedWindows(1, stdDev = 0.05f, firstWindowEnd = end + windowMs)
        assertEquals(runStart, analyzer.sleepOnsetTime)
    }

    @Test
    fun `screen use resets the still run`() {
        val analyzer = newAnalyzer(onsetWindows = 20)
        var end = analyzer.feedWindows(15, stdDev = 0.05f)
        // Checking the phone mid-run: still device but screen on.
        end = analyzer.feedWindows(1, stdDev = 0.05f, screenWasOn = true, firstWindowEnd = end + windowMs)
        analyzer.feedWindows(19, stdDev = 0.05f, firstWindowEnd = end + windowMs)
        assertNull(analyzer.sleepOnsetTime)
    }

    @Test
    fun `onset is not overwritten by later still runs`() {
        val analyzer = newAnalyzer(onsetWindows = 2)
        var end = analyzer.feedWindows(2, stdDev = 0.05f)
        val firstOnset = analyzer.sleepOnsetTime
        assertEquals(startTime, firstOnset)
        // Wake up in the night, then fall asleep again.
        end = analyzer.feedWindows(3, stdDev = 3.0f, firstWindowEnd = end + windowMs)
        analyzer.feedWindows(5, stdDev = 0.05f, firstWindowEnd = end + windowMs)
        assertEquals(firstOnset, analyzer.sleepOnsetTime)
    }

    @Test
    fun `records stage transitions and finish closes the last stage`() {
        val analyzer = newAnalyzer()
        var end = startTime + windowMs
        analyzer.onWindow(stdDev = 3.0f, screenWasOn = false, windowEnd = end) // AWAKE (no change)
        end += windowMs
        analyzer.onWindow(stdDev = 1.0f, screenWasOn = false, windowEnd = end) // -> DOZING
        end += windowMs
        analyzer.onWindow(stdDev = 0.05f, screenWasOn = false, windowEnd = end) // -> DEEP

        val finishTime = end + windowMs
        val records = analyzer.finish(finishTime)

        assertEquals(3, records.size)
        assertEquals(SleepStageType.AWAKE, records[0].stageType)
        assertEquals(startTime, records[0].startTime)
        assertEquals(SleepStageType.DOZING, records[1].stageType)
        assertEquals(SleepStageType.DEEP, records[2].stageType)
        assertEquals(finishTime, records[2].endTime)
        // Stages tile the whole session without gaps.
        records.zipWithNext { a, b -> assertEquals(a.endTime, b.startTime) }
    }

    @Test
    fun `finish is idempotent for the same end time`() {
        val analyzer = newAnalyzer()
        analyzer.feedWindows(3, stdDev = 0.05f)
        val first = analyzer.finish(startTime + 4 * windowMs)
        val second = analyzer.finish(startTime + 4 * windowMs)
        assertEquals(first, second)
    }
}
