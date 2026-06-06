package com.example.simplesleeprecorder.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import com.example.simplesleeprecorder.data.local.AlarmPreferenceKeys
import com.example.simplesleeprecorder.data.local.alarmDataStore
import com.example.simplesleeprecorder.data.repository.SleepRepository
import com.example.simplesleeprecorder.service.SleepSessionManager
import com.example.simplesleeprecorder.service.SleepTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(
    private val app: Application,
    private val sessionManager: SleepSessionManager,
    private val repository: SleepRepository,
) : ViewModel() {

    private val dataStore = app.alarmDataStore
    private val notificationPermGranted = MutableStateFlow(false)
    private val activityRecognitionPermGranted = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionManager.state,
                dataStore.data,
                notificationPermGranted,
                activityRecognitionPermGranted,
            ) { sessionState, prefs, notifGranted, activityGranted ->
                val hour = prefs[AlarmPreferenceKeys.ALARM_HOUR] ?: 7
                val minute = prefs[AlarmPreferenceKeys.ALARM_MINUTE] ?: 0
                val audioUri = prefs[AlarmPreferenceKeys.AUDIO_URI]
                val audioName = prefs[AlarmPreferenceKeys.AUDIO_DISPLAY_NAME]

                when (sessionState) {
                    is SleepSessionManager.SessionState.Idle -> HomeUiState.Idle(
                        alarmHour = hour,
                        alarmMinute = minute,
                        audioUri = audioUri,
                        audioDisplayName = audioName,
                        notificationPermissionGranted = notifGranted,
                        activityRecognitionPermissionGranted = activityGranted,
                    )
                    is SleepSessionManager.SessionState.Tracking -> HomeUiState.Tracking(
                        startTime = sessionState.startTime,
                        alarmTime = sessionState.alarmTime,
                        currentStage = sessionState.currentStage,
                        elapsedMs = sessionState.elapsedMs,
                        sleepOnsetTime = sessionState.sleepOnsetTime,
                    )
                    is SleepSessionManager.SessionState.AlarmRinging -> HomeUiState.AlarmRinging(
                        startTime = sessionState.startTime,
                        elapsedMs = sessionState.elapsedMs,
                    )
                    is SleepSessionManager.SessionState.SessionEnded ->
                        HomeUiState.SessionEnded(sessionState.sessionId)
                }
            }.collect { _uiState.value = it }
        }
    }

    fun setAlarmTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[AlarmPreferenceKeys.ALARM_HOUR] = hour
                prefs[AlarmPreferenceKeys.ALARM_MINUTE] = minute
            }
        }
    }

    fun setAudioUri(uri: String?, displayName: String?) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                if (uri != null) {
                    prefs[AlarmPreferenceKeys.AUDIO_URI] = uri
                    prefs[AlarmPreferenceKeys.AUDIO_DISPLAY_NAME] = displayName ?: ""
                } else {
                    prefs.remove(AlarmPreferenceKeys.AUDIO_URI)
                    prefs.remove(AlarmPreferenceKeys.AUDIO_DISPLAY_NAME)
                }
            }
        }
    }

    fun setNotificationPermissionGranted(granted: Boolean) {
        notificationPermGranted.value = granted
    }

    fun setActivityRecognitionPermissionGranted(granted: Boolean) {
        activityRecognitionPermGranted.value = granted
    }

    fun startTracking() {
        val idle = _uiState.value as? HomeUiState.Idle ?: return
        val alarmTime = computeAlarmTimeMs(idle.alarmHour, idle.alarmMinute)
        val intent = Intent(app, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START
            putExtra(SleepTrackingService.EXTRA_ALARM_TIME, alarmTime)
            idle.audioUri?.let { putExtra(SleepTrackingService.EXTRA_AUDIO_URI, it) }
        }
        app.startForegroundService(intent)
    }

    fun stopAlarm() {
        val intent = Intent(app, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_STOP_ALARM
        }
        app.startService(intent)
    }

    fun snooze(minutes: Int) {
        val intent = Intent(app, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_SNOOZE
            putExtra(SleepTrackingService.EXTRA_SNOOZE_MINUTES, minutes)
        }
        app.startService(intent)
    }

    fun cancelTracking() {
        val intent = Intent(app, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_CANCEL
        }
        app.startService(intent)
    }

    fun onSessionResultConsumed() {
        sessionManager.reset()
    }

    private fun computeAlarmTimeMs(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val alarm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!alarm.after(now)) alarm.add(Calendar.DAY_OF_YEAR, 1)
        return alarm.timeInMillis
    }
}
