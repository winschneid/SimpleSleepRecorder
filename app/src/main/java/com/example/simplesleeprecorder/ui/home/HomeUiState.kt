package com.example.simplesleeprecorder.ui.home

import com.example.simplesleeprecorder.domain.model.SleepStageType

sealed class HomeUiState {
    data class Idle(
        val alarmHour: Int = 7,
        val alarmMinute: Int = 0,
        val audioUri: String? = null,
        val audioDisplayName: String? = null,
        val smartAlarmEnabled: Boolean = false,
        val notificationPermissionGranted: Boolean = false,
        val activityRecognitionPermissionGranted: Boolean = false,
    ) : HomeUiState()

    data class Tracking(
        val startTime: Long,
        val alarmTime: Long,
        val currentStage: SleepStageType,
        val elapsedMs: Long,
        val sleepOnsetTime: Long?,
        val smartAlarmEnabled: Boolean = false,
    ) : HomeUiState()

    data class AlarmRinging(
        val startTime: Long,
        val elapsedMs: Long,
    ) : HomeUiState()

    data class SessionEnded(val sessionId: Long) : HomeUiState()
}
