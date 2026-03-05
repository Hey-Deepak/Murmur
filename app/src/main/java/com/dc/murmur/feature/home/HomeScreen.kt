package com.dc.murmur.feature.home

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.ui.components.AnalyzeNowCard
import com.dc.murmur.ui.components.TranscriptionCard
import com.dc.murmur.ui.components.VoiceTagDialog
import com.dc.murmur.ui.theme.DarkSurfaceCard
import com.dc.murmur.ui.theme.GradientRecording
import com.dc.murmur.ui.theme.GradientRecordingEnd
import com.dc.murmur.ui.theme.GradientTealEnd
import com.dc.murmur.ui.theme.GradientTealStart
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val todayChunks by viewModel.todayChunks.collectAsState()
    val todayCount by viewModel.todayCount.collectAsState()
    val todayDurationMs by viewModel.todayDurationMs.collectAsState()
    val todayStorageBytes by viewModel.todayStorageBytes.collectAsState()
    val analysisState by viewModel.analysisUiState.collectAsState()
    val analysisLog by viewModel.analysisLog.collectAsState()
    val unprocessedCount by viewModel.unprocessedCount.collectAsState()
    val recentTranscriptions by viewModel.recentTranscriptions.collectAsState()
    val chunkSpeakers by viewModel.chunkSpeakers.collectAsState()
    val playingProfileId by viewModel.playingProfileId.collectAsState()
    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsState()
    val taggedProfileNames by viewModel.taggedProfileNames.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagDialogProfileId by remember { mutableStateOf<Long?>(null) }
    var tagDialogCurrentLabel by remember { mutableStateOf<String?>(null) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Data") },
            text = { Text("This will delete all recordings, transcriptions, voice profiles, insights, and audio files. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllData(context)
                    showResetDialog = false
                }) { Text("Delete Everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTagDialog && tagDialogProfileId != null) {
        VoiceTagDialog(
            currentLabel = tagDialogCurrentLabel,
            onConfirm = { name ->
                viewModel.tagSpeaker(tagDialogProfileId!!, name)
                showTagDialog = false
                tagDialogProfileId = null
                tagDialogCurrentLabel = null
            },
            onDismiss = {
                showTagDialog = false
                tagDialogProfileId = null
                tagDialogCurrentLabel = null
            },
            suggestions = taggedProfileNames
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Murmur",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Reset all data",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isRecording) viewModel.stopRecording(context)
                    else viewModel.startRecording(context)
                },
                containerColor = if (isRecording) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isRecording) "Stop" else "Record"
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Recording status card with gradient
            item {
                Spacer(modifier = Modifier.height(8.dp))
                RecordingStatusCard(
                    isRecording = isRecording,
                    batteryLevel = viewModel.batteryLevel,
                    isCharging = viewModel.isCharging
                )
            }

            // Today's stats row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(
                        label = "Chunks",
                        value = "$todayCount",
                        icon = Icons.Filled.Layers,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = "Duration",
                        value = viewModel.formatDuration(todayDurationMs),
                        icon = Icons.Filled.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = "Size",
                        value = viewModel.formatBytes(todayStorageBytes),
                        icon = Icons.Filled.DataUsage,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Analyze Now card
            item {
                AnalyzeNowCard(
                    state = analysisState,
                    unprocessedCount = unprocessedCount,
                    onAnalyzeClick = { viewModel.startAnalysis(context) },
                    onCancelClick = { viewModel.cancelAnalysis(context) },
                    onReanalyzeAllClick = { viewModel.reanalyzeAll(context) },
                    logEntries = analysisLog
                )
            }

            // Recent Analysis section
            if (recentTranscriptions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(text = "Recent Analysis")
                        TextButton(onClick = { viewModel.clearRecentAnalysis() }) {
                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                items(recentTranscriptions) { transcription ->
                    TranscriptionCard(
                        transcription = transcription,
                        speakers = chunkSpeakers[transcription.chunkId] ?: emptyList(),
                        playingProfileId = if (isPlaying) playingProfileId else null,
                        onPlaySpeaker = { profileId ->
                            viewModel.playSpeaker(transcription.chunkId, profileId)
                        },
                        onTagSpeaker = { profileId ->
                            tagDialogProfileId = profileId
                            tagDialogCurrentLabel = chunkSpeakers[transcription.chunkId]
                                ?.find { it.voiceProfileId == profileId }?.profileLabel
                            showTagDialog = true
                        }
                    )
                }
            }

            // Recent recordings header
            item {
                SectionHeader(text = "Today's recordings")
            }

            if (todayChunks.isEmpty()) {
                item {
                    Text(
                        text = "No recordings yet today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(todayChunks.takeLast(10).reversed()) { chunk ->
                    ChunkCard(
                        fileName = chunk.fileName,
                        durationMs = chunk.durationMs,
                        sizeBytes = chunk.fileSizeBytes,
                        interruptedBy = chunk.interruptedBy,
                        formatDuration = viewModel::formatDuration,
                        formatBytes = viewModel::formatBytes
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun RecordingStatusCard(
    isRecording: Boolean,
    batteryLevel: Int,
    isCharging: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseAlpha by infiniteTransition.animateColor(
        initialValue = if (isRecording) GradientRecording else Color.Transparent,
        targetValue = if (isRecording) GradientRecordingEnd else Color.Transparent,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_color"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isRecording) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(
                                    pulseAlpha.copy(alpha = 0.25f),
                                    GradientRecordingEnd.copy(alpha = 0.10f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier.background(
                            DarkSurfaceCard,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.FiberManualRecord
                                  else Icons.Filled.MicOff,
                    contentDescription = null,
                    tint = if (isRecording) GradientRecording
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isRecording) "Recording..." else "Not recording",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) GradientRecording
                               else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Battery: $batteryLevel%${if (isCharging) " ⚡" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(GradientTealStart, GradientTealEnd)
                    )
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChunkCard(
    fileName: String,
    durationMs: Long,
    sizeBytes: Long,
    interruptedBy: String?,
    formatDuration: (Long) -> String,
    formatBytes: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatDuration(durationMs)} · ${formatBytes(sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (interruptedBy != null) {
                    Text(
                        text = "⚠ $interruptedBy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
