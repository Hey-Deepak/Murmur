package com.dc.murmur.feature.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordingsViewModel(
    private val recordingRepo: RecordingRepository,
    private val analysisRepo: AnalysisRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val chunks: StateFlow<List<RecordingChunkEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) recordingRepo.getAllChunks()
            else recordingRepo.searchChunks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDates: StateFlow<List<String>> = recordingRepo.getAllDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transcript previews: chunkId -> preview text
    private val _transcriptionPreviews = MutableStateFlow<Map<Long, String>>(emptyMap())
    val transcriptionPreviews: StateFlow<Map<Long, String>> = _transcriptionPreviews.asStateFlow()

    fun onSearchQuery(query: String) { _searchQuery.value = query }

    fun deleteChunk(chunk: RecordingChunkEntity) {
        viewModelScope.launch { recordingRepo.deleteChunk(chunk) }
    }

    fun deleteAllChunks() {
        viewModelScope.launch { recordingRepo.deleteAllChunks() }
    }

    fun loadTranscriptionPreview(chunkId: Long) {
        if (_transcriptionPreviews.value.containsKey(chunkId)) return
        viewModelScope.launch {
            val twc = analysisRepo.getTranscriptionForChunk(chunkId)
            if (twc != null && twc.text.isNotBlank()) {
                _transcriptionPreviews.value = _transcriptionPreviews.value + (chunkId to twc.text)
            }
        }
    }

    fun chunksForDate(date: String): List<RecordingChunkEntity> =
        chunks.value.filter { it.date == date }

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val h = m / 60
        return if (h > 0) "%dh %02dm".format(h, m % 60) else "%dm %02ds".format(m, s % 60)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000     -> "%.0f KB".format(bytes / 1_000.0)
        else               -> "$bytes B"
    }
}
