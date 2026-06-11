package com.example.simplesleeprecorder.domain

import com.example.simplesleeprecorder.domain.model.SleepStageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAlarmPolicyTest {

    private val alarmTime = 7 * 60 * 60 * 1000L // 07:00 relative epoch
    private val windowMs = SmartAlarmPolicy.DEFAULT_WINDOW_MS

    private fun trigger(
        stage: SleepStageType = SleepStageType.LIGHT,
        hasSlept: Boolean = true,
        now: Long = alarmTime - 10 * 60 * 1000L, // 10 min before alarm
    ) = SmartAlarmPolicy.shouldTriggerEarly(
        hasSleptThisSession = hasSlept,
        currentStage = stage,
        now = now,
        alarmTime = alarmTime,
        windowMs = windowMs,
    )

    @Test
    fun `fires in light sleep within the window`() {
        assertTrue(trigger(stage = SleepStageType.LIGHT))
        assertTrue(trigger(stage = SleepStageType.DOZING))
    }

    @Test
    fun `fires when stirring awake within the window`() {
        assertTrue(trigger(stage = SleepStageType.AWAKE))
    }

    @Test
    fun `never fires during deep sleep`() {
        assertFalse(trigger(stage = SleepStageType.DEEP))
    }

    @Test
    fun `never fires before the window opens`() {
        assertFalse(trigger(now = alarmTime - windowMs - 1))
        assertTrue(trigger(now = alarmTime - windowMs))
    }

    @Test
    fun `never fires at or after the set alarm time`() {
        assertFalse(trigger(now = alarmTime))
        assertFalse(trigger(now = alarmTime + 1))
    }

    @Test
    fun `never fires if the user has not fallen asleep this session`() {
        assertFalse(trigger(hasSlept = false))
    }
}
