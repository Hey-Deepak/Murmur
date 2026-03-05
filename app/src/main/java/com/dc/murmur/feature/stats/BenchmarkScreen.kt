package com.dc.murmur.feature.stats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dc.murmur.ai.rust.BenchmarkComparison
import com.dc.murmur.ai.rust.StageTiming
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    onBack: () -> Unit,
    viewModel: BenchmarkViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val results by viewModel.results.collectAsState()
    val averageSpeedup by viewModel.averageSpeedup.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var selectedChunkCount by remember { mutableIntStateOf(3) }

    // Models dir on device (ONNX diarization models — same as sherpa-onnx)
    val modelsDir = remember { context.filesDir.resolve("sherpa-models").absolutePath }
    val nativeLibDir = remember { context.applicationInfo.nativeLibraryDir }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pipeline Benchmark") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (results.isNotEmpty()) {
                        IconButton(onClick = {
                            val json = viewModel.resultsToJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Benchmark Results", json))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy results")
                        }
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Library status
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Rust Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (viewModel.isLibraryLoaded)
                                    Color(0xFF4CAF50).copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    if (viewModel.isLibraryLoaded) "Loaded" else "Not loaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (viewModel.isLibraryLoaded) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (!viewModel.isLibraryLoaded) {
                            Text(
                                viewModel.libraryLoadError ?: "libmurmur_rs.so not found in jniLibs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Controls
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Chunk Count", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(1, 3, 5).forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = selectedChunkCount == count,
                                    onClick = { selectedChunkCount = count },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                                ) {
                                    Text("$count")
                                }
                            }
                        }

                        val isRunning = state == BenchmarkState.RUNNING ||
                                state == BenchmarkState.INITIALIZING ||
                                state == BenchmarkState.LOADING_LIBRARY

                        Button(
                            onClick = { viewModel.startBenchmark(selectedChunkCount, modelsDir, nativeLibDir) },
                            enabled = viewModel.isLibraryLoaded && !isRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(when (state) {
                                BenchmarkState.LOADING_LIBRARY -> "Loading library..."
                                BenchmarkState.INITIALIZING -> "Initializing..."
                                BenchmarkState.RUNNING -> "Running..."
                                else -> "Start Benchmark"
                            })
                        }

                        if (isRunning) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                progress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state == BenchmarkState.COMPLETED && results.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.reset() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Error
            errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Average summary
            if (results.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Average Speedup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${String.format("%.2f", averageSpeedup)}x",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${results.size} chunk(s) benchmarked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Per-chunk results
            itemsIndexed(results) { index, comparison ->
                BenchmarkResultCard(index + 1, comparison)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun BenchmarkResultCard(number: Int, comparison: BenchmarkComparison) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Chunk #$number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (comparison.speedup >= 1.0) Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "${String.format("%.2f", comparison.speedup)}x",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (comparison.speedup >= 1.0) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                "Audio: ${String.format("%.1f", comparison.audioDurationSec)}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            comparison.rustPeakMemoryKb?.let { kb ->
                Text(
                    "Peak memory (Rust): ${String.format("%.1f", kb / 1024.0)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Results table
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState())
                ) {
                    // Header
                    TimingRow("Stage", "Kotlin (ms)", "Rust (ms)", "Speedup", isHeader = true)

                    // Merge stages by name
                    val allStageNames = (comparison.kotlinStages.map { it.stage } +
                            comparison.rustStages.map { it.stage }).distinct()

                    for (stageName in allStageNames) {
                        val kotlinMs = comparison.kotlinStages.find { it.stage == stageName }?.durationMs
                        val rustMs = comparison.rustStages.find { it.stage == stageName }?.durationMs
                        val stageSpeedup = if (kotlinMs != null && rustMs != null && rustMs > 0) {
                            "${String.format("%.1f", kotlinMs.toDouble() / rustMs.toDouble())}x"
                        } else "-"

                        TimingRow(
                            stage = stageName,
                            kotlinMs = kotlinMs?.toString() ?: "-",
                            rustMs = rustMs?.toString() ?: "-",
                            speedup = stageSpeedup
                        )
                    }

                    // Total row
                    val totalSpeedup = if (comparison.rustTotalMs > 0) {
                        "${String.format("%.1f", comparison.kotlinTotalMs.toDouble() / comparison.rustTotalMs.toDouble())}x"
                    } else "-"
                    TimingRow("TOTAL", "${comparison.kotlinTotalMs}", "${comparison.rustTotalMs}", totalSpeedup, isBold = true)
                }
            }
        }
    }
}

@Composable
private fun TimingRow(
    stage: String,
    kotlinMs: String,
    rustMs: String,
    speedup: String,
    isHeader: Boolean = false,
    isBold: Boolean = false
) {
    val weight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal
    val style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = weight
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stage.padEnd(12), style = style, modifier = Modifier.width(100.dp))
        Text(kotlinMs, style = style, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
        Text(rustMs, style = style, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
        Text(speedup, style = style, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
    }
}
