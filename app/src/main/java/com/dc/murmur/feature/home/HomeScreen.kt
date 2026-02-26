package com.dc.murmur.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.ui.components.AnalyzeNowCard
import com.dc.murmur.ui.components.TranscriptionCard
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
    val unprocessedCount by viewModel.unprocessedCount.collectAsState()
    val recentTranscriptions by viewModel.recentTranscriptions.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Murmur") }) },
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
            // Status card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isRecording) "Recording..." else "Not recording",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Battery: ${viewModel.batteryLevel}%${if (viewModel.isCharging) " ⚡" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Today's stats row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(label = "Chunks", value = "$todayCount", modifier = Modifier.weight(1f))
                    StatChip(label = "Duration", value = viewModel.formatDuration(todayDurationMs), modifier = Modifier.weight(1f))
                    StatChip(label = "Size", value = viewModel.formatBytes(todayStorageBytes), modifier = Modifier.weight(1f))
                }
            }

            // Analyze Now card
            item {
                AnalyzeNowCard(
                    state = analysisState,
                    unprocessedCount = unprocessedCount,
                    onAnalyzeClick = { viewModel.startAnalysis(context) },
                    onCancelClick = { viewModel.cancelAnalysis(context) }
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
                        Text(
                            text = "Recent Analysis",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.clearRecentAnalysis() }) {
                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                items(recentTranscriptions) { transcription ->
                    TranscriptionCard(transcription = transcription)
                }
            }

            // Recent recordings header
            item {
                Text(
                    text = "Today's recordings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
