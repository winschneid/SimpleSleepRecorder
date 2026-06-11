package com.example.simplesleeprecorder.domain

import com.example.simplesleeprecorder.domain.model.SleepStageType

/**
 * Decides whether the alarm should fire early, ahead of the set time, to
 * catch the user in light sleep. Kept free of Android dependencies for unit
 * testing, like [SleepStageAnalyzer].
 *
 * The alarm may fire within [DEFAULT_WINDOW_MS] before the set time, once the
 * user has actually fallen asleep this session, in any stage except DEEP —
 * waking from light sleep (or while already stirring) feels far gentler than
 * being pulled out of deep sleep at the exact alarm time.
 */
object SmartAlarmPolicy {

    /** How long before the set alarm time the early-wake window opens. */
    const val DEFAULT_WINDOW_MS = 30 * 60 * 1000L

    fun shouldTriggerEarly(
        hasSleptThisSession: Boolean,
        currentStage: SleepStageType,
        now: Long,
        alarmTime: Long,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): Boolean {
        if (!hasSleptThisSession) return false
        if (now < alarmTime - windowMs || now >= alarmTime) return false
        return currentStage != SleepStageType.DEEP
    }
}
