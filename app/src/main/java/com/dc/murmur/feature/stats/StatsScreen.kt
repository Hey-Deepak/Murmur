package com.dc.murmur.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dc.murmur.ai.BridgeStatus
import com.dc.murmur.ai.BridgeUiState
import com.dc.murmur.ai.ModelDownloadState
import com.dc.murmur.ai.SpeechModelInfo
import com.dc.murmur.ai.SpeechProvider
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.dc.murmur.ui.components.AnalyzeNowCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = koinViewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val totalStorage by viewModel.totalStorageBytes.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val analysisEnabled by viewModel.analysisEnabled.collectAsState()
    val requireCharging by viewModel.requireCharging.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val autoDeleteDays by viewModel.autoDeleteDays.collectAsState()
    val analysisMode by viewModel.analysisMode.collectAsState()
    val minBattery by viewModel.minBattery.collectAsState()
    val analysisHour by viewModel.analysisHour.collectAsState()
    val analysisMinute by viewModel.analysisMinute.collectAsState()
    val analysisDays by viewModel.analysisDays.collectAsState()
    val batteryLogs by viewModel.todayBatteryLogs.collectAsState()
    val analysisState by viewModel.analysisUiState.collectAsState()
    val unprocessedCount by viewModel.unprocessedCount.collectAsState()

    // Speech model state
    val modelStates by viewModel.modelDownloadStates.collectAsState()
    val activeModelId by viewModel.activeModelId.collectAsState()

    val analysisLog by viewModel.analysisLog.collectAsState()

    // Bridge state
    val bridgeState by viewModel.bridgeUiState.collectAsState()
    val bridgePort by viewModel.claudeBridgePort.collectAsState()
    val bridgeAutoStart by viewModel.claudeBridgeAutoStart.collectAsState()
    val isTermuxInstalled by viewModel.isTermuxInstalled.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Stats & Settings") }) }) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Battery Chart ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Battery Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (batteryLogs.size >= 2) {
                            val entries = batteryLogs.mapIndexed { i, log ->
                                Pair(i.toFloat(), log.batteryLevel.toFloat())
                            }
                            ProvideChartStyle(m3ChartStyle()) {
                                Chart(
                                    chart = lineChart(),
                                    model = entryModelOf(*entries.map { it.first to it.second }.toTypedArray()),
                                    startAxis = rememberStartAxis(),
                                    bottomAxis = rememberBottomAxis(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                            }
                        } else {
                            Text(
                                "Not enough data yet — start recording to see battery trends",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- Storage ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Total used: ${viewModel.formatBytes(totalStorage)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // --- Speech Model ---
            item {
                SpeechModelCard(
                    models = viewModel.modelCatalog,
                    modelStates = modelStates,
                    activeModelId = activeModelId,
                    onDownload = viewModel::downloadModel,
                    onSetActive = viewModel::setActiveModel,
                    onDelete = { showDeleteConfirm = it },
                    formatBytes = viewModel::formatBytes
                )
            }

            // --- Recording Settings ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        // Auto-start toggle
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-start on boot")
                            Switch(checked = autoStartOnBoot, onCheckedChange = viewModel::setAutoStartOnBoot)
                        }

                        // Audio quality selector
                        Text("Audio quality", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("low", "normal", "high").forEachIndexed { index, quality ->
                                SegmentedButton(
                                    selected = audioQuality == quality,
                                    onClick = { viewModel.setAudioQuality(quality) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                                ) {
                                    Text(quality.replaceFirstChar { it.uppercase() })
                                }
                            }
                        }

                        // Auto-delete selector
                        Text("Auto-delete after", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(7, 14, 30).forEachIndexed { index, days ->
                                SegmentedButton(
                                    selected = autoDeleteDays == days,
                                    onClick = { viewModel.setAutoDeleteDays(days) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                                ) {
                                    Text("$days days")
                                }
                            }
                        }
                    }
                }
            }

            // --- Analysis Settings ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        // Enable toggle
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable analysis")
                            Switch(checked = analysisEnabled, onCheckedChange = viewModel::setAnalysisEnabled)
                        }

                        // Analyze Now button
                        AnalyzeNowCard(
                            state = analysisState,
                            unprocessedCount = unprocessedCount,
                            onAnalyzeClick = { viewModel.startAnalysis(context) },
                            onCancelClick = { viewModel.cancelAnalysis(context) }
                        )

                        // Live Analysis Log
                        if (analysisLog.isNotEmpty()) {
                            AnalysisLogCard(logEntries = analysisLog)
                        }

                        if (analysisEnabled) {
                            // Trigger mode
                            Text("Trigger mode", style = MaterialTheme.typography.labelMedium)
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                listOf(
                                    "fixed_time" to "Fixed time",
                                    "on_charging" to "When charging",
                                    "manual" to "Manual only"
                                ).forEach { (mode, label) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = analysisMode == mode,
                                            onClick = { viewModel.setAnalysisMode(mode) }
                                        )
                                        Text(label, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }

                            // Time picker (only for fixed_time mode)
                            if (analysisMode == "fixed_time") {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Analysis time")
                                    TextButton(onClick = { showTimePicker = true }) {
                                        Text(
                                            "%d:%02d %s".format(
                                                if (analysisHour % 12 == 0) 12 else analysisHour % 12,
                                                analysisMinute,
                                                if (analysisHour < 12) "AM" else "PM"
                                            )
                                        )
                                    }
                                }

                                // Day chips
                                Text("Days", style = MaterialTheme.typography.labelMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                                        val selected = day in analysisDays
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                val newDays = if (selected) analysisDays - day else analysisDays + day
                                                if (newDays.isNotEmpty()) viewModel.setAnalysisDays(newDays)
                                            },
                                            label = { Text(day.take(1)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Require charging toggle
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Require charging")
                                Switch(checked = requireCharging, onCheckedChange = viewModel::setRequireCharging)
                            }

                            // Min battery slider
                            Text("Minimum battery: $minBattery%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = minBattery.toFloat(),
                                onValueChange = { viewModel.setMinBattery(it.toInt()) },
                                valueRange = 10f..50f,
                                steps = 7
                            )
                        }
                    }
                }
            }

            // --- Claude Bridge ---
            item {
                ClaudeBridgeCard(
                    bridgeState = bridgeState,
                    isTermuxInstalled = isTermuxInstalled,
                    port = bridgePort,
                    autoStart = bridgeAutoStart,
                    onStartClick = viewModel::startBridge,
                    onStopClick = viewModel::stopBridge,
                    onRefreshClick = viewModel::checkBridgeStatus,
                    onInstallScripts = viewModel::installBridgeScripts,
                    onPortChange = viewModel::setClaudeBridgePort,
                    onAutoStartChange = viewModel::setClaudeBridgeAutoStart
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = analysisHour,
            initialMinute = analysisMinute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAnalysisTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirm?.let { modelId ->
        val modelInfo = viewModel.modelCatalog.find { it.id == modelId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Model") },
            text = { Text("Delete ${modelInfo?.language ?: modelId} (${modelInfo?.provider?.name ?: ""}) model? You can re-download it later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteModel(modelId)
                    showDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SpeechModelCard(
    models: List<SpeechModelInfo>,
    modelStates: Map<String, ModelDownloadState>,
    activeModelId: String,
    onDownload: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onDelete: (String) -> Unit,
    formatBytes: (Long) -> String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Speech Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Download and select a speech recognition model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            models.forEach { model ->
                val state = modelStates[model.id] ?: ModelDownloadState.NotDownloaded
                val isActive = model.id == activeModelId

                SpeechModelRow(
                    model = model,
                    state = state,
                    isActive = isActive,
                    onSelect = { onSetActive(model.id) },
                    onDownload = { onDownload(model.id) },
                    onDelete = { onDelete(model.id) },
                    formatBytes = formatBytes
                )
            }
        }
    }
}

@Composable
private fun SpeechModelRow(
    model: SpeechModelInfo,
    state: ModelDownloadState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    formatBytes: (Long) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = isActive,
            onClick = if (state is ModelDownloadState.Ready) onSelect else null,
            enabled = state is ModelDownloadState.Ready
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(model.language, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (model.provider) {
                        SpeechProvider.VOSK -> MaterialTheme.colorScheme.secondaryContainer
                        SpeechProvider.WHISPER -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    modifier = Modifier
                ) {
                    Text(
                        model.provider.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                "~${formatBytes(model.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Download progress
            if (state is ModelDownloadState.Downloading) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        // Action button
        when {
            isActive && state is ModelDownloadState.Ready -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            state is ModelDownloadState.Ready && !isActive -> {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            state is ModelDownloadState.NotDownloaded -> {
                TextButton(onClick = onDownload) {
                    Text("Get")
                }
            }
            state is ModelDownloadState.Downloading -> {
                Text(
                    "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClaudeBridgeCard(
    bridgeState: BridgeUiState,
    isTermuxInstalled: Boolean,
    port: Int,
    autoStart: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onInstallScripts: () -> Unit,
    onPortChange: (Int) -> Unit,
    onAutoStartChange: (Boolean) -> Unit
) {
    val status = bridgeState.status
    val isTransitioning = status == BridgeStatus.STARTING || status == BridgeStatus.STOPPING

    val statusColor = when (status) {
        BridgeStatus.RUNNING -> Color(0xFF4CAF50)   // green
        BridgeStatus.STARTING,
        BridgeStatus.STOPPING -> Color(0xFFFFC107)   // amber
        BridgeStatus.STOPPED -> Color(0xFFF44336)    // red
        BridgeStatus.UNKNOWN -> Color.Gray
    }

    val statusText = when (status) {
        BridgeStatus.RUNNING -> "Running"
        BridgeStatus.STARTING -> "Starting..."
        BridgeStatus.STOPPING -> "Stopping..."
        BridgeStatus.STOPPED -> "Stopped"
        BridgeStatus.UNKNOWN -> "Unknown"
    }

    var portText by remember(port) { mutableStateOf(port.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Title row with status dot and refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Claude Bridge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onRefreshClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh status",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!isTermuxInstalled) {
                Text(
                    "Termux is not installed. Install Termux from F-Droid to use the Claude Bridge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                // Error message
                bridgeState.errorMessage?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Start / Stop button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status == BridgeStatus.RUNNING) {
                        Button(
                            onClick = onStopClick,
                            enabled = !isTransitioning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop Bridge")
                        }
                    } else {
                        Button(
                            onClick = onStartClick,
                            enabled = !isTransitioning,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTransitioning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (status == BridgeStatus.STARTING) "Starting..." else "Start Bridge")
                        }
                    }
                }

                // Install / Update Scripts
                OutlinedButton(
                    onClick = onInstallScripts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install / Update Scripts")
                }

                // Port field
                OutlinedTextField(
                    value = portText,
                    onValueChange = { value ->
                        portText = value
                        val parsed = value.toIntOrNull()
                        if (parsed != null && parsed in 1024..65535) {
                            onPortChange(parsed)
                        }
                    },
                    label = { Text("Bridge port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = portText.toIntOrNull()?.let { it !in 1024..65535 } ?: true,
                    supportingText = {
                        if (portText.toIntOrNull()?.let { it !in 1024..65535 } != false) {
                            Text("Port must be 1024-65535")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Auto-start toggle
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-start with recording")
                    Switch(checked = autoStart, onCheckedChange = onAutoStartChange)
                }
            }
        }
    }
}

@Composable
private fun AnalysisLogCard(logEntries: List<String>) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when new entries are added
    LaunchedEffect(logEntries.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Live Log",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(8.dp)
                ) {
                    logEntries.forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
