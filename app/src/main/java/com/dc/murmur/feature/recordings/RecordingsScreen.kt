package com.dc.murmur.feature.recordings

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(viewModel: RecordingsViewModel = koinViewModel()) {
    val query by viewModel.searchQuery.collectAsState()
    val allChunks by viewModel.chunks.collectAsState()
    val grouped = allChunks.groupBy { it.date }
    val dates = grouped.keys.sortedDescending()
    var searchActive by remember { mutableStateOf(false) }
    val player = remember { AudioPlayer() }
    var chunkToDelete by remember { mutableStateOf<RecordingChunkEntity?>(null) }

    DisposableEffect(Unit) { onDispose { player.destroy() } }

    chunkToDelete?.let { chunk ->
        AlertDialog(
            onDismissRequest = { chunkToDelete = null },
            title = { Text("Delete Recording") },
            text = { Text("Delete \"${chunk.fileName}\"? The audio file will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChunk(chunk)
                    chunkToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { chunkToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recordings") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchBar(
                query = query,
                onQueryChange = viewModel::onSearchQuery,
                onSearch = { searchActive = false },
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = { Text("Search recordings") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            if (dates.isEmpty()) {
                Text(
                    text = "No recordings yet",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dates.forEach { date ->
                        val chunksForDate = grouped[date].orEmpty()
                        item(key = date) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        items(chunksForDate, key = { it.id }) { chunk ->
                                val isPlaying by player.isPlaying.collectAsState()
                                val currentFile by player.currentFilePath.collectAsState()
                                val isThisPlaying = isPlaying && currentFile == chunk.filePath

                                RecordingChunkCard(
                                    fileName = chunk.fileName,
                                    duration = viewModel.formatDuration(chunk.durationMs),
                                    size = viewModel.formatBytes(chunk.fileSizeBytes),
                                    interruptedBy = chunk.interruptedBy,
                                    isPlaying = isThisPlaying,
                                    onPlayPause = { player.togglePlayPause(chunk.filePath) },
                                    onDelete = { chunkToDelete = chunk },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                )
                            }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecordingChunkCard(
    fileName: String,
    duration: String,
    size: String,
    interruptedBy: String?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
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
                    text = "$duration · $size",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (interruptedBy != null) {
                    Text(
                        text = interruptedBy,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
