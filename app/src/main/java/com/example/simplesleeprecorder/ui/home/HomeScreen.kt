package com.example.simplesleeprecorder.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simplesleeprecorder.domain.model.SleepStageType
import com.example.simplesleeprecorder.ui.theme.DeepSleepColor
import com.example.simplesleeprecorder.ui.theme.DozingColor
import com.example.simplesleeprecorder.ui.theme.LightSleepColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSessionEnded: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is HomeUiState.SessionEnded) {
            onSessionEnded((uiState as HomeUiState.SessionEnded).sessionId)
            viewModel.onSessionResultConsumed()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationPermissionGranted(granted)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.setNotificationPermissionGranted(granted)
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.setNotificationPermissionGranted(true)
        }
    }

    AnimatedContent(
        targetState = uiState::class,
        label = "home_state",
    ) { _ ->
        when (val state = uiState) {
            is HomeUiState.Idle -> IdleContent(
                state = state,
                onAlarmTimeSet = viewModel::setAlarmTime,
                onAudioSelected = { uri, name -> viewModel.setAudioUri(uri, name) },
                onStartTracking = viewModel::startTracking,
            )
            is HomeUiState.Tracking -> TrackingContent(
                state = state,
                onCancel = viewModel::cancelTracking,
            )
            is HomeUiState.AlarmRinging -> AlarmRingingContent(
                state = state,
                onStopAlarm = viewModel::stopAlarm,
            )
            is HomeUiState.SessionEnded -> Box(Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleContent(
    state: HomeUiState.Idle,
    onAlarmTimeSet: (Int, Int) -> Unit,
    onAudioSelected: (String?, String?) -> Unit,
    onStartTracking: () -> Unit,
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = state.alarmHour,
        initialMinute = state.alarmMinute,
        is24Hour = true,
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
            onAudioSelected(uri.toString(), displayName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Nightlight,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "睡眠計測",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "うとうと・すやすや・ぐっすりを計測します",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("設定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("起床アラーム")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(
                            text = "%02d:%02d".format(state.alarmHour, state.alarmMinute),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("アラーム音")
                            if (state.audioDisplayName != null) {
                                Text(
                                    text = state.audioDisplayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    text = "デフォルト",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    }) {
                        Text("選択")
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onStartTracking,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Default.Nightlight, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("計測開始", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            state = timePickerState,
            onConfirm = {
                onAlarmTimeSet(timePickerState.hour, timePickerState.minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun TrackingContent(
    state: HomeUiState.Tracking,
    onCancel: () -> Unit,
) {
    val stageBgColor = when (state.currentStage) {
        SleepStageType.AWAKE -> MaterialTheme.colorScheme.surface
        SleepStageType.DOZING -> DozingColor.copy(alpha = 0.15f)
        SleepStageType.LIGHT -> LightSleepColor.copy(alpha = 0.15f)
        SleepStageType.DEEP -> DeepSleepColor.copy(alpha = 0.15f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(stageBgColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "計測中",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Text(
            text = state.currentStage.label,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = when (state.currentStage) {
                SleepStageType.AWAKE -> MaterialTheme.colorScheme.onSurface
                SleepStageType.DOZING -> DozingColor
                SleepStageType.LIGHT -> LightSleepColor
                SleepStageType.DEEP -> DeepSleepColor
            },
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = formatElapsed(state.elapsedMs),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Light,
        )

        if (state.sleepOnsetTime != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "入眠まで: ${formatElapsed(state.sleepOnsetTime - state.startTime)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        val alarmStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.alarmTime))
        Text(
            text = "アラーム: $alarmStr",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(48.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("計測をキャンセル")
        }
    }
}

@Composable
private fun AlarmRingingContent(
    state: HomeUiState.AlarmRinging,
    onStopAlarm: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "おはようございます！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = formatElapsed(state.elapsedMs),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(48.dp))

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape,
                    )
            )
            Button(
                onClick = onStopAlarm,
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("止める", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    state: TimePickerState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("起床時刻を設定", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("キャンセル") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = onConfirm) { Text("確定") }
                }
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) "%d時間%02d分%02d秒".format(hours, minutes, seconds)
    else "%02d分%02d秒".format(minutes, seconds)
}
