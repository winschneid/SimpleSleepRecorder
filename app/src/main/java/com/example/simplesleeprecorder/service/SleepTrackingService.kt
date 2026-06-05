package com.example.simplesleeprecorder.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.simplesleeprecorder.MainActivity
import com.example.simplesleeprecorder.SimpleSleepRecorderApp
import com.example.simplesleeprecorder.domain.model.SleepSession
import com.example.simplesleeprecorder.domain.model.SleepStageRecord
import com.example.simplesleeprecorder.domain.model.SleepStageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class SleepTrackingService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
        const val ACTION_SNOOZE = "ACTION_SNOOZE"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val ACTION_ALARM_TRIGGERED = "ACTION_ALARM_TRIGGERED"

        const val EXTRA_ALARM_TIME = "EXTRA_ALARM_TIME"
        const val EXTRA_AUDIO_URI = "EXTRA_AUDIO_URI"
        const val EXTRA_SNOOZE_MINUTES = "EXTRA_SNOOZE_MINUTES"

        const val CHANNEL_ID_TRACKING = "SLEEP_TRACKING"
        const val CHANNEL_ID_ALARM = "SLEEP_ALARM"
        const val NOTIFICATION_ID = 1001

        private const val WINDOW_MS = 30_000L
        private const val TICKER_MS = 1_000L

        private const val THRESHOLD_AWAKE = 2.0f
        private const val THRESHOLD_DOZING = 0.5f
        private const val THRESHOLD_LIGHT = 0.1f

        private const val SLEEP_ONSET_CONSECUTIVE_WINDOWS = 2
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private lateinit var sensorManager: SensorManager
    private lateinit var notificationManager: NotificationManager
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var sessionManager: SleepSessionManager
    private lateinit var app: SimpleSleepRecorderApp

    private var sessionStartTime = 0L
    private var alarmTime = 0L
    private var audioUri: String? = null

    private val magnitudeSamples = mutableListOf<Float>()
    private var windowStartTime = 0L
    private val stageRecords = mutableListOf<SleepStageRecord>()
    private var currentStage = SleepStageType.AWAKE
    private var currentStageStartTime = 0L
    private var sleepOnsetTime: Long? = null
    private var consecutiveNonAwakeWindows = 0

    override fun onCreate() {
        super.onCreate()
        app = applicationContext as SimpleSleepRecorderApp
        sessionManager = app.sessionManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                alarmTime = intent.getLongExtra(EXTRA_ALARM_TIME, 0L)
                audioUri = intent.getStringExtra(EXTRA_AUDIO_URI)
                startTracking()
            }
            ACTION_ALARM_TRIGGERED -> handleAlarmTriggered()
            ACTION_STOP_ALARM -> handleStopAlarm()
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
                handleSnooze(minutes)
            }
            ACTION_CANCEL -> handleCancel()
        }
        return START_STICKY
    }

    private fun startTracking() {
        sessionStartTime = System.currentTimeMillis()
        windowStartTime = sessionStartTime
        currentStage = SleepStageType.AWAKE
        currentStageStartTime = sessionStartTime
        stageRecords.clear()
        magnitudeSamples.clear()
        sleepOnsetTime = null
        consecutiveNonAwakeWindows = 0

        sessionManager.startSession(sessionStartTime, alarmTime, audioUri)

        val notification = buildTrackingNotification()
        startForeground(NOTIFICATION_ID, notification)

        acquireWakeLock()

        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        scheduleAlarm()
        startElapsedTicker()
    }

    private fun handleAlarmTriggered() {
        sessionManager.triggerAlarm()
        stopAlarmMedia()
        startAlarmMedia()
        notificationManager.notify(NOTIFICATION_ID, buildAlarmNotification())
    }

    private fun handleStopAlarm() {
        stopAlarmMedia()
        finishSession()
    }

    private fun handleSnooze(minutes: Int) {
        stopAlarmMedia()
        val newAlarmTime = System.currentTimeMillis() + minutes * 60_000L
        alarmTime = newAlarmTime
        sessionManager.snooze(newAlarmTime)
        scheduleAlarm()
        notificationManager.notify(NOTIFICATION_ID, buildTrackingNotification())
    }

    private fun handleCancel() {
        stopAlarmMedia()
        sensorManager.unregisterListener(this)
        cancelAlarm()
        tickerJob?.cancel()
        releaseWakeLock()
        sessionManager.reset()
        stopSelf()
    }

    private fun finishSession() {
        val endTime = System.currentTimeMillis()
        sensorManager.unregisterListener(this)
        cancelAlarm()
        tickerJob?.cancel()

        // Close the current open stage
        if (currentStageStartTime < endTime) {
            stageRecords.add(
                SleepStageRecord(
                    sessionId = 0,
                    stageType = currentStage,
                    startTime = currentStageStartTime,
                    endTime = endTime,
                )
            )
        }

        scope.launch {
            val session = SleepSession(
                startTime = sessionStartTime,
                endTime = endTime,
                alarmTime = alarmTime,
                sleepOnsetTime = sleepOnsetTime,
                audioUri = audioUri,
                stageRecords = stageRecords.toList(),
            )
            val sessionId = app.repository.saveSession(session)
            sessionManager.endSession(sessionId)
            releaseWakeLock()
            stopSelf()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        magnitudeSamples.add(magnitude)

        val now = System.currentTimeMillis()
        if (now - windowStartTime >= WINDOW_MS) {
            processWindow(now)
            magnitudeSamples.clear()
            windowStartTime = now
        }
    }

    private fun processWindow(now: Long) {
        if (magnitudeSamples.isEmpty()) return
        val stdDev = computeStdDev(magnitudeSamples)
        val newStage = classifyStage(stdDev)

        if (newStage != SleepStageType.AWAKE) {
            consecutiveNonAwakeWindows++
            if (sleepOnsetTime == null && consecutiveNonAwakeWindows >= SLEEP_ONSET_CONSECUTIVE_WINDOWS) {
                sleepOnsetTime = currentStageStartTime
            }
        } else {
            consecutiveNonAwakeWindows = 0
        }

        if (newStage != currentStage) {
            stageRecords.add(
                SleepStageRecord(
                    sessionId = 0,
                    stageType = currentStage,
                    startTime = currentStageStartTime,
                    endTime = now,
                )
            )
            currentStage = newStage
            currentStageStartTime = now
        }

        sessionManager.updateTracking(
            currentStage = currentStage,
            elapsedMs = now - sessionStartTime,
            sleepOnsetTime = sleepOnsetTime,
        )
    }

    private fun computeStdDev(samples: List<Float>): Float {
        if (samples.size < 2) return 0f
        val mean = samples.average().toFloat()
        val variance = samples.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    private fun classifyStage(stdDev: Float): SleepStageType = when {
        stdDev > THRESHOLD_AWAKE -> SleepStageType.AWAKE
        stdDev > THRESHOLD_DOZING -> SleepStageType.DOZING
        stdDev > THRESHOLD_LIGHT -> SleepStageType.LIGHT
        else -> SleepStageType.DEEP
    }

    private fun startElapsedTicker() {
        tickerJob = scope.launch {
            while (true) {
                delay(TICKER_MS)
                val current = sessionManager.state.value
                val elapsed = System.currentTimeMillis() - sessionStartTime
                if (current is SleepSessionManager.SessionState.Tracking) {
                    sessionManager.updateTracking(
                        currentStage = currentStage,
                        elapsedMs = elapsed,
                        sleepOnsetTime = sleepOnsetTime,
                    )
                }
            }
        }
    }

    private fun scheduleAlarm() {
        if (alarmTime <= System.currentTimeMillis()) return
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun startAlarmMedia() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val uri = audioUri?.let { Uri.parse(it) }
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback to default alarm if custom audio fails
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    val defaultUri = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_ALARM
                    )
                    setDataSource(applicationContext, defaultUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (ex: Exception) {
                // Ignore if even default alarm fails
            }
        }
    }

    private fun stopAlarmMedia() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SimpleSleepRecorder:SleepTracking"
        ).apply { acquire(12 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildTrackingNotification(): Notification {
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_TRACKING)
            .setContentTitle("睡眠計測中")
            .setContentText("睡眠を記録しています")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildAlarmNotification(): Notification {
        val stopIntent = Intent(this, SleepTrackingService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenIntent = PendingIntent.getActivity(
            this, 2, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle("おはようございます！")
            .setContentText("アラームが鳴っています")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, "アラームを止める", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        val trackingChannel = NotificationChannel(
            CHANNEL_ID_TRACKING,
            "睡眠計測",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "睡眠計測中の通知" }

        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            "アラーム",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "アラーム通知"
            setBypassDnd(true)
        }

        notificationManager.createNotificationChannel(trackingChannel)
        notificationManager.createNotificationChannel(alarmChannel)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        stopAlarmMedia()
        tickerJob?.cancel()
        releaseWakeLock()
    }
}
