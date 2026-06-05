package com.example.simplesleeprecorder.ui.result

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simplesleeprecorder.domain.model.SleepSession
import com.example.simplesleeprecorder.ui.theme.DeepSleepColor
import com.example.simplesleeprecorder.ui.theme.DozingColor
import com.example.simplesleeprecorder.ui.theme.LightSleepColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ResultScreen(
    sessionId: Long,
    viewModel: ResultViewModel,
    onDone: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    when (val state = uiState) {
        is ResultUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ResultUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("データの読み込みに失敗しました")
        }
        is ResultUiState.Success -> ResultContent(session = state.session, onDone = onDone)
    }
}

@Composable
private fun ResultContent(session: SleepSession, onDone: () -> Unit) {
    val dateFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val totalSleepMs = session.totalSleepMs
    val dozingMs = session.dozingMs
    val lightMs = session.lightMs
    val deepMs = session.deepMs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "睡眠レポート",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(session.startTime)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("概要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TimeStatItem(
                        icon = { Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        label = "就寝",
                        value = dateFmt.format(Date(session.startTime)),
                    )
                    TimeStatItem(
                        icon = { Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                        label = "起床",
                        value = dateFmt.format(Date(session.endTime)),
                    )
                    TimeStatItem(
                        icon = null,
                        label = "睡眠時間",
                        value = formatDuration(totalSleepMs),
                    )
                }
                val sleepOnsetMs = session.sleepOnsetMs
                if (sleepOnsetMs != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = "寝付くまでの時間: ${formatDuration(sleepOnsetMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("睡眠ステージ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))

                SleepStageBar(
                    label = "うとうと",
                    color = DozingColor,
                    durationMs = dozingMs,
                    totalMs = totalSleepMs,
                )
                Spacer(Modifier.height(10.dp))
                SleepStageBar(
                    label = "すやすや",
                    color = LightSleepColor,
                    durationMs = lightMs,
                    totalMs = totalSleepMs,
                )
                Spacer(Modifier.height(10.dp))
                SleepStageBar(
                    label = "ぐっすり",
                    color = DeepSleepColor,
                    durationMs = deepMs,
                    totalMs = totalSleepMs,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("閉じる", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TimeStatItem(
    icon: (@Composable () -> Unit)?,
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            icon()
            Spacer(Modifier.height(4.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SleepStageBar(
    label: String,
    color: Color,
    durationMs: Long,
    totalMs: Long,
) {
    val fraction = if (totalMs > 0) durationMs.toFloat() / totalMs else 0f
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "%.0f%%".format(fraction * 100),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}時間${minutes}分" else "${minutes}分"
}
