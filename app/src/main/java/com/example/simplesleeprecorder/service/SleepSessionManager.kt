package com.example.simplesleeprecorder.service

import com.example.simplesleeprecorder.domain.model.SleepStageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SleepSessionManager {

    sealed class SessionState {
        data object Idle : SessionState()
        data class Tracking(
            val startTime: Long,
            val alarmTime: Long,
            val audioUri: String?,
            val currentStage: SleepStageType,
            val elapsedMs: Long,
            val sleepOnsetTime: Long?,
        ) : SessionState()
        data class AlarmRinging(
            val startTime: Long,
            val alarmTime: Long,
            val audioUri: String?,
            val currentStage: SleepStageType,
            val elapsedMs: Long,
            val sleepOnsetTime: Long?,
        ) : SessionState()
        data class SessionEnded(val sessionId: Long) : SessionState()
    }

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun startSession(startTime: Long, alarmTime: Long, audioUri: String?) {
        _state.value = SessionState.Tracking(
            startTime = startTime,
            alarmTime = alarmTime,
            audioUri = audioUri,
            currentStage = SleepStageType.AWAKE,
            elapsedMs = 0L,
            sleepOnsetTime = null,
        )
    }

    fun updateTracking(
        currentStage: SleepStageType,
        elapsedMs: Long,
        sleepOnsetTime: Long?,
    ) {
        val current = _state.value
        if (current is SessionState.Tracking) {
            _state.value = current.copy(
                currentStage = currentStage,
                elapsedMs = elapsedMs,
                sleepOnsetTime = sleepOnsetTime,
            )
        }
    }

    fun triggerAlarm() {
        val current = _state.value
        if (current is SessionState.Tracking) {
            _state.value = SessionState.AlarmRinging(
                startTime = current.startTime,
                alarmTime = current.alarmTime,
                audioUri = current.audioUri,
                currentStage = current.currentStage,
                elapsedMs = current.elapsedMs,
                sleepOnsetTime = current.sleepOnsetTime,
            )
        }
    }

    fun endSession(sessionId: Long) {
        _state.value = SessionState.SessionEnded(sessionId)
    }

    fun reset() {
        _state.value = SessionState.Idle
    }
}
