package com.dc.murmur.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BridgeStatus {
    UNKNOWN,
    RUNNING,
    STOPPED,
    STARTING,
    STOPPING
}

data class BridgeUiState(
    val status: BridgeStatus = BridgeStatus.UNKNOWN,
    val lastChecked: Long = 0L,
    val errorMessage: String? = null
)

class BridgeStatusHolder {
    private val _state = MutableStateFlow(BridgeUiState())
    val state: StateFlow<BridgeUiState> = _state.asStateFlow()

    fun setStarting() {
        _state.value = BridgeUiState(status = BridgeStatus.STARTING)
    }

    fun setStopping() {
        _state.value = BridgeUiState(status = BridgeStatus.STOPPING)
    }

    fun setRunning() {
        _state.value = BridgeUiState(
            status = BridgeStatus.RUNNING,
            lastChecked = System.currentTimeMillis()
        )
    }

    fun setStopped() {
        _state.value = BridgeUiState(
            status = BridgeStatus.STOPPED,
            lastChecked = System.currentTimeMillis()
        )
    }

    fun setError(message: String) {
        _state.value = BridgeUiState(
            status = BridgeStatus.STOPPED,
            lastChecked = System.currentTimeMillis(),
            errorMessage = message
        )
    }

    fun setUnknown() {
        _state.value = BridgeUiState(status = BridgeStatus.UNKNOWN)
    }
}
