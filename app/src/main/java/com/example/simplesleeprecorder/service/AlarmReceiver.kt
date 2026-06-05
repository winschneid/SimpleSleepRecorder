package com.example.simplesleeprecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_ALARM_TRIGGERED
        }
        context.startForegroundService(serviceIntent)
    }
}
