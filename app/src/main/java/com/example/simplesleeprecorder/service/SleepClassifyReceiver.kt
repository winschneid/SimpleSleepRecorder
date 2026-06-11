package com.example.simplesleeprecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.SleepClassifyEvent

/**
 * Receives SleepClassifyEvents from the Sleep API (delivered roughly every
 * 10 minutes while subscribed) and forwards the latest confidence to
 * [SleepTrackingService], which fuses it into the staging windows.
 */
class SleepClassifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SleepClassifyEvent.hasEvents(intent)) return
        val latest = SleepClassifyEvent.extractEvents(intent)
            .maxByOrNull { it.timestampMillis } ?: return
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_SLEEP_CLASSIFY
            putExtra(SleepTrackingService.EXTRA_SLEEP_CONFIDENCE, latest.confidence)
            putExtra(SleepTrackingService.EXTRA_SLEEP_CONFIDENCE_TIME, latest.timestampMillis)
        }
        context.startService(serviceIntent)
    }
}
