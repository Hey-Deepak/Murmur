package com.dc.murmur.feature.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.ai.AnalysisStateHolder
import com.dc.murmur.ai.AnalysisUiState
import com.dc.murmur.ai.AnalysisWorker
import com.dc.murmur.core.constants.AppConstants
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import com.dc.murmur.data.local.entity.TranscriptionEntity
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.BatteryRepository
import com.dc.murmur.data.repository.RecordingRepository
import com.dc.murmur.data.repository.SettingsRepository
import com.dc.murmur.service.RecordingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val recordingRepo: RecordingRepository,
    private val batteryRepo: BatteryRepository,
    private val settingsRepo: SettingsRepository,
    private val analysisRepo: AnalysisRepository,
    private val analysisState: AnalysisStateHolder
) : ViewModel() {

    val isRecording: StateFlow<Boolean> = recordingRepo.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val todayChunks: StateFlow<List<RecordingChunkEntity>> = recordingRepo.getTodayChunks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayCount: StateFlow<Int> = recordingRepo.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayDurationMs: StateFlow<Long> = recordingRepo.getTodayDurationMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayStorageBytes: StateFlow<Long> = recordingRepo.getTodayStorageBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val analysisUiState: StateFlow<AnalysisUiState> = analysisState.state

    val analysisLog: StateFlow<List<String>> = analysisState.logEntries

    val unprocessedCount: StateFlow<Int> = analysisRepo.getUnprocessedCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentTranscriptions: StateFlow<List<TranscriptionEntity>> =
        analysisRepo.getRecentTranscriptions(5)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batteryLevel: Int get() = batteryRepo.getCurrentLevel()
    val isCharging: Boolean get() = batteryRepo.isCharging()

    fun startRecording(context: Context) {
        context.startForegroundService(
            Intent(context, RecordingService::class.java).apply {
                action = AppConstants.ACTION_START_RECORDING
            }
        )
    }

    fun stopRecording(context: Context) {
        context.startService(
            Intent(context, RecordingService::class.java).apply {
                action = AppConstants.ACTION_STOP_RECORDING
            }
        )
    }

    fun startAnalysis(context: Context) {
        analysisState.setIdle()
        AnalysisWorker.enqueueNow(context)
    }

    fun reanalyzeAll(context: Context) {
        viewModelScope.launch {
            analysisRepo.markAllForReanalysis()
            analysisState.setIdle()
            AnalysisWorker.enqueueNow(context)
        }
    }

    fun cancelAnalysis(context: Context) {
        AnalysisWorker.cancel(context)
        analysisState.setIdle()
    }

    fun clearRecentAnalysis() {
        viewModelScope.launch {
            analysisRepo.clearAllTranscriptions()
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000         -> "%.0f KB".format(bytes / 1_000.0)
        else                   -> "$bytes B"
    }
}
