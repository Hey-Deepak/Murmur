package com.dc.murmur.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AnalysisStatus {
    IDLE,
    DOWNLOADING_MODELS,
    RUNNING,
    COMPLETED,
    ERROR
}

data class AnalysisUiState(
    val status: AnalysisStatus = AnalysisStatus.IDLE,
    val progress: Int = 0,
    val total: Int = 0,
    val currentStep: String = "",
    val errorMessage: String? = null
)

class AnalysisStateHolder {
    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "[$timestamp] $message"
        _logEntries.value = _logEntries.value + entry
    }

    fun clearLog() {
        _logEntries.value = emptyList()
    }

    fun setDownloading() {
        _state.value = AnalysisUiState(status = AnalysisStatus.DOWNLOADING_MODELS)
    }

    fun setRunning(progress: Int, total: Int, step: String = "") {
        _state.value = AnalysisUiState(
            status = AnalysisStatus.RUNNING,
            progress = progress,
            total = total,
            currentStep = step
        )
    }

    fun setStep(step: String) {
        val current = _state.value
        if (current.status == AnalysisStatus.RUNNING) {
            _state.value = current.copy(currentStep = step)
        }
    }

    fun setCompleted() {
        _state.value = AnalysisUiState(status = AnalysisStatus.COMPLETED)
    }

    fun setError(message: String) {
        _state.value = AnalysisUiState(
            status = AnalysisStatus.ERROR,
            errorMessage = message
        )
    }

    fun setIdle() {
        _state.value = AnalysisUiState(status = AnalysisStatus.IDLE)
    }
}
