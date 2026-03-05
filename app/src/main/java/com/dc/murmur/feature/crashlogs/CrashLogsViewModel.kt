package com.dc.murmur.feature.crashlogs

import androidx.lifecycle.ViewModel
import com.dc.murmur.core.util.CrashEntry
import com.dc.murmur.core.util.CrashLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CrashLogsViewModel : ViewModel() {

    private val _crashes = MutableStateFlow<List<CrashEntry>>(emptyList())
    val crashes: StateFlow<List<CrashEntry>> = _crashes

    init {
        refresh()
    }

    fun refresh() {
        _crashes.value = CrashLogger.getCrashLogs()
    }

    fun markFixed(filename: String) {
        CrashLogger.markFixed(filename)
        refresh()
    }

    fun deleteAll() {
        CrashLogger.deleteAll()
        refresh()
    }

    fun deleteFixed() {
        CrashLogger.deleteFixed()
        refresh()
    }
}
