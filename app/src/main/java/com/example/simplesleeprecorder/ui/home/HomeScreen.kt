package com.example.simplesleeprecorder.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.example.simplesleeprecorder.domain.SmartAlarmPolicy
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

    val activityRecognitionPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setActivityRecognitionPermissionGranted(granted)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.setActivityRecognitionPermissionGranted(granted)
            if (!granted) {
                activityRecognitionPermLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        } else {
            viewModel.setActivityRecognitionPermissionGranted(true)
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
                onSmartAlarmChanged = viewModel::setSmartAlarmEnabled,
                onStartTracking = viewModel::startTracking,
                activityRecognitionGranted = state.activityRecognitionPermissionGranted,
            )
            is HomeUiState.Tracking -> TrackingContent(
                state = state,
                onFinish = viewModel::finishTracking,
            )
            is HomeUiState.AlarmRinging -> AlarmRingingContent(
                state = state,
                onStopAlarm = viewModel::stopAlarm,
                onSnooze = viewModel::snooze,
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
    onSmartAlarmChanged: (Boolean) -> Unit,
    onStartTracking: () -> Unit,
    activityRecognitionGranted: Boolean,
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }

    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val storagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showAudioPicker = true }

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
        Spacer(Modifier.height(4.dp))
        Text(
            text = "計測中はスマホを枕元のマットレスの上に置いてください",
            style = MaterialTheme.typography.bodySmall,
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("アラーム音")
                            if (state.audioDisplayName != null) {
                                Text(
                                    text = stripTrackNumber(state.audioDisplayName),
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
                        val granted = ContextCompat.checkSelfPermission(
                            context, storagePermission
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) showAudioPicker = true
                        else storagePermLauncher.launch(storagePermission)
                    }) {
                        Text("選択")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("スマートアラーム")
                            Text(
                                text = "設定時刻前${SmartAlarmPolicy.DEFAULT_WINDOW_MS / 60_000}分以内の眠りが浅いタイミングで起こします",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = state.smartAlarmEnabled,
                        onCheckedChange = onSmartAlarmChanged,
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onStartTracking,
            enabled = activityRecognitionGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Default.Nightlight, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                if (activityRecognitionGranted) "計測開始" else "パーミッションが必要です",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    if (showTimePicker) {
        // Create the state when the dialog opens so it always starts from the
        // current saved time. (rememberTimePickerState only honours its initial
        // values on first composition, so a hoisted instance would keep the
        // 7:00 default captured before DataStore loaded the saved value.)
        val timePickerState = rememberTimePickerState(
            initialHour = state.alarmHour,
            initialMinute = state.alarmMinute,
            is24Hour = true,
        )
        TimePickerDialog(
            state = timePickerState,
            onConfirm = {
                onAlarmTimeSet(timePickerState.hour, timePickerState.minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showAudioPicker) {
        AudioPickerDialog(
            onAudioSelected = { uri, name ->
                onAudioSelected(uri, name)
                showAudioPicker = false
            },
            onDismiss = { showAudioPicker = false },
        )
    }
}

@Composable
private fun TrackingContent(
    state: HomeUiState.Tracking,
    onFinish: () -> Unit,
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

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val alarmStr = timeFmt.format(Date(state.alarmTime))
        if (state.smartAlarmEnabled) {
            val windowStartStr = timeFmt.format(
                Date(state.alarmTime - SmartAlarmPolicy.DEFAULT_WINDOW_MS)
            )
            Text(
                text = "アラーム: $windowStartStr〜$alarmStr",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "眠りが浅いタイミングで鳴ります",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "アラーム: $alarmStr",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "スマホを枕元に置いて画面を消してください\n（画面が点いている間は「起きている」として記録されます）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        OutlinedButton(
            onClick = onFinish,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("計測を終了")
        }
    }
}

@Composable
private fun AlarmRingingContent(
    state: HomeUiState.AlarmRinging,
    onStopAlarm: () -> Unit,
    onSnooze: (Int) -> Unit,
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

        Spacer(Modifier.height(32.dp))

        Text(
            text = "スヌーズ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onSnooze(5) }) {
                Text("5分")
            }
            OutlinedButton(onClick = { onSnooze(10) }) {
                Text("10分")
            }
        }
    }
}

@Composable
private fun AudioPickerDialog(
    onAudioSelected: (String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioFiles by produceState<List<AudioItem>>(emptyList()) {
        value = withContext(Dispatchers.IO) { queryAudioFiles(context) }
    }
    var converting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun selectAudio(audio: AudioItem) {
        if (audio.mimeType in AIFF_MIME_TYPES) {
            // AIFF can't be played directly; convert it to WAV first.
            converting = true
            errorMessage = null
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { convertAiffToWav(context, audio.uri) }
                }
                converting = false
                result.onSuccess { wavUri -> onAudioSelected(wavUri, audio.displayName) }
                    .onFailure { errorMessage = "この形式は変換できませんでした" }
            }
        } else {
            onAudioSelected(audio.uri, audio.displayName)
        }
    }

    Dialog(onDismissRequest = { if (!converting) onDismiss() }) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            Column {
                Text(
                    "アラーム音を選択",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                HorizontalDivider()
                if (converting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("変換中…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    errorMessage?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    LazyColumn {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAudioSelected(null, null) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(12.dp))
                                Text("デフォルト（システムアラーム）", style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }
                        if (audioFiles.isEmpty()) {
                            item {
                                Text(
                                    "音楽ファイルが見つかりません",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(audioFiles) { audio ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectAudio(audio) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.size(12.dp))
                                    Text(audio.displayName, style = MaterialTheme.typography.bodyMedium)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

private val trackNumberRegex = Regex("""^\d+[.\s]\s*""")

private fun stripTrackNumber(name: String): String =
    trackNumberRegex.replace(name, "").ifEmpty { name }

private data class AudioItem(val uri: String, val displayName: String, val mimeType: String)

// AIFF can't be played by MediaPlayer directly, but it's uncompressed PCM, so we
// convert it to WAV on selection (see AiffToWavConverter).
private val AIFF_MIME_TYPES = setOf("audio/x-aiff", "audio/aiff")

// MIME types we offer in the picker: formats MediaPlayer can decode natively,
// plus AIFF (converted on selection). Other formats (e.g. WMA, APE) fail to play
// and fall back to the default alarm sound, so we hide them. See
// developer.android.com/media/platform/supported-formats
private val PLAYABLE_AUDIO_MIME_TYPES = arrayOf(
    "audio/mpeg", "audio/mp4", "audio/aac", "audio/x-m4a", "audio/mp4a-latm",
    "audio/flac", "audio/x-flac", "audio/ogg", "application/ogg", "audio/opus",
    "audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/3gpp",
    "audio/amr", "audio/amr-wb", "audio/midi", "audio/x-midi",
) + AIFF_MIME_TYPES

private fun queryAudioFiles(context: android.content.Context): List<AudioItem> {
    val items = mutableListOf<AudioItem>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.MIME_TYPE,
    )
    val placeholders = PLAYABLE_AUDIO_MIME_TYPES.joinToString(",") { "?" }
    val selection = "${MediaStore.Audio.Media.MIME_TYPE} IN ($placeholders)"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection, PLAYABLE_AUDIO_MIME_TYPES,
        "${MediaStore.Audio.Media.TITLE} ASC",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol)
            val name = cursor.getString(nameCol)
            val mime = cursor.getString(mimeCol) ?: ""
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            val rawName = title ?: name ?: "Unknown"
            items.add(AudioItem(uri = uri.toString(), displayName = stripTrackNumber(rawName), mimeType = mime))
        }
    }
    return items
}

/**
 * Converts an AIFF file at [sourceUri] to a WAV in the app's cache and returns
 * its file:// uri (string). Throws if the AIFF is a compressed variant.
 */
private fun convertAiffToWav(context: android.content.Context, sourceUri: String): String {
    val outFile = java.io.File(context.cacheDir, "alarm_sound.wav")
    context.contentResolver.openInputStream(android.net.Uri.parse(sourceUri)).use { input ->
        requireNotNull(input) { "Cannot open $sourceUri" }
        java.io.FileOutputStream(outFile).use { output ->
            com.example.simplesleeprecorder.audio.AiffToWavConverter.convert(input, output)
        }
    }
    return android.net.Uri.fromFile(outFile).toString()
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
