package com.dc.murmur.feature.transcriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.data.local.entity.TranscriptionWithChunk
import com.dc.murmur.data.repository.AnalysisRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranscriptionsViewModel(
    private val analysisRepo: AnalysisRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transcriptions: StateFlow<List<TranscriptionWithChunk>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) analysisRepo.getAllTranscriptionsWithChunks()
            else analysisRepo.searchTranscriptionsWithChunks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQuery(query: String) { _searchQuery.value = query }

    fun deleteTranscription(id: Long) {
        viewModelScope.launch { analysisRepo.deleteTranscription(id) }
    }

    fun formatTimestamp(epochMs: Long): String {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val h = m / 60
        return if (h > 0) "%dh %02dm".format(h, m % 60) else "%dm %02ds".format(m, s % 60)
    }
}
