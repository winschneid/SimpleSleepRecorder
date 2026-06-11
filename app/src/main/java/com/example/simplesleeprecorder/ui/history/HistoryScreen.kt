package com.example.simplesleeprecorder.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simplesleeprecorder.domain.SleepStatsCalculator
import com.example.simplesleeprecorder.domain.model.SleepSession
import com.example.simplesleeprecorder.domain.model.SleepStageType
import com.example.simplesleeprecorder.ui.theme.DeepSleepColor
import com.example.simplesleeprecorder.ui.theme.DozingColor
import com.example.simplesleeprecorder.ui.theme.LightSleepColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }

    when (val state = uiState) {
        is HistoryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is HistoryUiState.Success -> {
            if (state.sessions.isEmpty()) {
                EmptyHistory()
            } else {
                val stats = remember(state.sessions) {
                    SleepStatsCalculator.forLastDays(state.sessions)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    if (stats != null) {
                        item { WeeklyStatsCard(stats) }
                    }
                    items(state.sessions, key = { it.id }) { session ->
                        SleepSessionCard(
                            session = session,
                            onDelete = { deleteTargetId = session.id },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("削除の確認") },
            text = { Text("このデータを削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(deleteTargetId!!)
                    deleteTargetId = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun WeeklyStatsCard(stats: SleepStatsCalculator.Stats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "直近7日間",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("平均睡眠時間", formatDuration(stats.averageSleepMs))
                StatItem("平均スコア", "${stats.averageScore}点")
                StatItem("平均入眠時間", stats.averageOnsetMs?.let { formatDuration(it) } ?: "--")
            }
            Spacer(Modifier.height(16.dp))
            DailySleepBarChart(stats.dailySleep)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DailySleepBarChart(dailySleep: List<SleepStatsCalculator.DailySleep>) {
    val barAreaHeight = 80.dp
    // Scale against at least 8h so a normal night doesn't always fill the chart.
    val maxMs = maxOf(dailySleep.maxOf { it.totalSleepMs }, 8 * 3_600_000L)
    val dayFmt = SimpleDateFormat("E", Locale.JAPANESE)
    val barColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dailySleep.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barAreaHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (day.totalSleepMs > 0) {
                        val fraction = (day.totalSleepMs.toFloat() / maxMs).coerceIn(0.04f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 3.dp)
                                .height(barAreaHeight * fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor.copy(alpha = 0.75f)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dayFmt.format(Date(day.dayStartMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (day.totalSleepMs > 0) {
                        "%.1f".format(day.totalSleepMs / 3_600_000f)
                    } else "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "記録がありません",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "睡眠を計測すると過去データが表示されます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SleepSessionCard(session: SleepSession, onDelete: () -> Unit) {
    val dateFmt = SimpleDateFormat("yyyy/MM/dd (E)", Locale.JAPANESE)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = dateFmt.format(Date(session.sleepDateMillis)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = " ${timeFmt.format(Date(session.startTime))} → ${timeFmt.format(Date(session.endTime))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatDuration(session.totalSleepMs),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ScoreBadge(score = session.sleepScore)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StagePill(label = "うとうと", durationMs = session.dozingMs, color = DozingColor, modifier = Modifier.weight(1f))
                StagePill(label = "すやすや", durationMs = session.lightMs, color = LightSleepColor, modifier = Modifier.weight(1f))
                StagePill(label = "ぐっすり", durationMs = session.deepMs, color = DeepSleepColor, modifier = Modifier.weight(1f))
            }
            if (session.stageRecords.isNotEmpty()) {
                val totalMs = (session.endTime - session.startTime).coerceAtLeast(1L)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp)),
                ) {
                    session.stageRecords.forEach { record ->
                        val fraction = record.durationMs.toFloat() / totalMs
                        Box(
                            modifier = Modifier
                                .weight(fraction.coerceAtLeast(0.002f))
                                .fillMaxHeight()
                                .background(stageColor(record.stageType)),
                        )
                    }
                }
            }
            val sleepOnsetMs = session.sleepOnsetMs
            if (sleepOnsetMs != null && sleepOnsetMs > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "入眠時間: ${formatDuration(sleepOnsetMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StagePill(
    label: String,
    durationMs: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val scoreColor = when {
        score >= 80 -> Color(0xFF43A047)
        score >= 60 -> Color(0xFF1E88E5)
        score >= 40 -> Color(0xFFFB8C00)
        else        -> Color(0xFFE53935)
    }
    Box(
        modifier = Modifier
            .background(scoreColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "${score}点",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = scoreColor,
        )
    }
}

private fun stageColor(stage: SleepStageType): Color = when (stage) {
    SleepStageType.AWAKE -> Color(0xFFBDBDBD)
    SleepStageType.DOZING -> DozingColor
    SleepStageType.LIGHT -> LightSleepColor
    SleepStageType.DEEP -> DeepSleepColor
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}
