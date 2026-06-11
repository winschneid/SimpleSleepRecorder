package com.example.simplesleeprecorder.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarm_prefs")

object AlarmPreferenceKeys {
    val ALARM_HOUR = intPreferencesKey("alarm_hour")
    val ALARM_MINUTE = intPreferencesKey("alarm_minute")
    val AUDIO_URI = stringPreferencesKey("audio_uri")
    val AUDIO_DISPLAY_NAME = stringPreferencesKey("audio_display_name")
    val SMART_ALARM_ENABLED = booleanPreferencesKey("smart_alarm_enabled")
}
