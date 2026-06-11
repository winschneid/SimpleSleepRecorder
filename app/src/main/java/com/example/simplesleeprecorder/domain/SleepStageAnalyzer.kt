package com.example.simplesleeprecorder.domain

import com.example.simplesleeprecorder.domain.model.SleepStageRecord
import com.example.simplesleeprecorder.domain.model.SleepStageType
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sleep-staging state machine, fed one accelerometer window at a time.
 * Kept free of Android dependencies so the staging and sleep-onset rules
 * can be unit tested apart from [com.example.simplesleeprecorder.service.SleepTrackingService].
 *
 * Sleep onset is the start of the first sustained still period: a run of
 * [onsetConsecutiveWindows] consecutive windows that are non-AWAKE with the
 * screen off. With 30s windows the default of 20 means 10 minutes of
 * stillness, in line with standard actigraphy sleep-onset rules — a phone
 * simply lying still on the nightstand no longer counts as falling asleep
 * after a minute.
 *
 * Thread-safety: windows arrive on the sensor thread while snapshots are read
 * from coroutines, hence the volatile fields and copy-on-write record list.
 */
class SleepStageAnalyzer(
    private val onsetConsecutiveWindows: Int = ONSET_CONSECUTIVE_WINDOWS,
) {

    companion object {
        private const val THRESHOLD_AWAKE = 2.0f
        private const val THRESHOLD_DOZING = 0.5f
        private const val THRESHOLD_LIGHT = 0.1f

        /** 20 windows x 30s = 10 minutes of sustained stillness. */
        const val ONSET_CONSECUTIVE_WINDOWS = 20

        // Sleep API confidence (0-100) fusion thresholds. Only the confident
        // extremes override motion staging; in between, motion decides.
        /** At or below this confidence the user is treated as awake. */
        const val SLEEP_API_AWAKE_MAX_CONFIDENCE = 25
        /** At or above this confidence a motion-AWAKE window stays asleep. */
        const val SLEEP_API_ASLEEP_MIN_CONFIDENCE = 75
    }

    private val records = CopyOnWriteArrayList<SleepStageRecord>()

    @Volatile
    var currentStage: SleepStageType = SleepStageType.AWAKE
        private set

    @Volatile
    var sleepOnsetTime: Long? = null
        private set

    private var currentStageStartTime = 0L
    private var consecutiveStillWindows = 0
    private var stillRunStartTime = 0L
    private var lastWindowEnd = 0L

    /** Completed stage records so far (excludes the in-progress stage). */
    val stageRecords: List<SleepStageRecord>
        get() = records.toList()

    fun start(startTime: Long) {
        records.clear()
        currentStage = SleepStageType.AWAKE
        currentStageStartTime = startTime
        sleepOnsetTime = null
        consecutiveStillWindows = 0
        lastWindowEnd = startTime
    }

    /**
     * Processes the window ending at [windowEnd] (and starting where the
     * previous one ended). [screenWasOn] marks that the screen was interactive
     * at some point during the window — the user was using the phone, so they
     * were awake no matter how still the device was.
     *
     * [sleepApiConfidence] is the latest fresh Sleep API confidence (0-100)
     * from Google's on-device model (motion + ambient light), or null when
     * unavailable. It corrects the two cases motion alone gets wrong: lying
     * still while awake (e.g. phone parked on the nightstand while the user
     * reads) and brief movement while asleep (turning over).
     */
    fun onWindow(
        stdDev: Float,
        screenWasOn: Boolean,
        windowEnd: Long,
        sleepApiConfidence: Int? = null,
    ) {
        val motionStage = classify(stdDev)
        val newStage = when {
            // User interaction trumps everything, including the Sleep API.
            screenWasOn -> SleepStageType.AWAKE
            sleepApiConfidence != null &&
                sleepApiConfidence <= SLEEP_API_AWAKE_MAX_CONFIDENCE -> SleepStageType.AWAKE
            motionStage == SleepStageType.AWAKE &&
                sleepApiConfidence != null &&
                sleepApiConfidence >= SLEEP_API_ASLEEP_MIN_CONFIDENCE -> SleepStageType.DOZING
            else -> motionStage
        }

        if (newStage != SleepStageType.AWAKE) {
            if (consecutiveStillWindows == 0) {
                stillRunStartTime = lastWindowEnd
            }
            consecutiveStillWindows++
            if (sleepOnsetTime == null && consecutiveStillWindows >= onsetConsecutiveWindows) {
                sleepOnsetTime = stillRunStartTime
            }
        } else {
            consecutiveStillWindows = 0
        }

        if (newStage != currentStage) {
            records.add(
                SleepStageRecord(
                    sessionId = 0,
                    stageType = currentStage,
                    startTime = currentStageStartTime,
                    endTime = windowEnd,
                )
            )
            currentStage = newStage
            currentStageStartTime = windowEnd
        }
        lastWindowEnd = windowEnd
    }

    /** Closes out the in-progress stage and returns the complete record list. */
    fun finish(endTime: Long): List<SleepStageRecord> {
        if (currentStageStartTime < endTime) {
            records.add(
                SleepStageRecord(
                    sessionId = 0,
                    stageType = currentStage,
                    startTime = currentStageStartTime,
                    endTime = endTime,
                )
            )
            currentStageStartTime = endTime
        }
        return records.toList()
    }

    private fun classify(stdDev: Float): SleepStageType = when {
        stdDev > THRESHOLD_AWAKE -> SleepStageType.AWAKE
        stdDev > THRESHOLD_DOZING -> SleepStageType.DOZING
        stdDev > THRESHOLD_LIGHT -> SleepStageType.LIGHT
        else -> SleepStageType.DEEP
    }
}
