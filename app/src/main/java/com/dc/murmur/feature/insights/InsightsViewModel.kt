package com.dc.murmur.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.ai.InsightGenerator
import com.dc.murmur.data.local.dao.ActivityTimeBreakdown
import com.dc.murmur.data.local.entity.ActivityEntity
import com.dc.murmur.data.local.entity.DailyInsightEntity
import com.dc.murmur.data.local.entity.PredictionEntity
import com.dc.murmur.data.local.entity.TopicEntity
import com.dc.murmur.data.local.entity.TranscriptionWithChunk
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class InsightsViewModel(
    private val insightsRepo: InsightsRepository,
    private val analysisRepo: AnalysisRepository,
    private val recordingRepo: RecordingRepository,
    private val peopleRepo: PeopleRepository,
    private val insightGenerator: InsightGenerator
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val _viewMode = MutableStateFlow("daily")
    val viewMode: StateFlow<String> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(dateFormat.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyInsight: StateFlow<DailyInsightEntity?> = _selectedDate
        .flatMapLatest { date -> insightsRepo.getDailyInsightFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyActivities: StateFlow<List<ActivityEntity>> = _selectedDate
        .flatMapLatest { date -> insightsRepo.getActivitiesByDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyTranscriptions: StateFlow<List<TranscriptionWithChunk>> = _selectedDate
        .flatMapLatest { analysisRepo.getAllTranscriptionsWithChunks() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _timeBreakdown = MutableStateFlow<List<ActivityTimeBreakdown>>(emptyList())
    val timeBreakdown: StateFlow<List<ActivityTimeBreakdown>> = _timeBreakdown.asStateFlow()

    val weeklyInsights: StateFlow<List<DailyInsightEntity>> = insightsRepo.getRecentInsights(7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyTopTopics: StateFlow<List<TopicEntity>> = insightsRepo.getTopTopics(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _weeklyTimeBreakdown = MutableStateFlow<List<ActivityTimeBreakdown>>(emptyList())
    val weeklyTimeBreakdown: StateFlow<List<ActivityTimeBreakdown>> = _weeklyTimeBreakdown.asStateFlow()

    val predictions: StateFlow<List<PredictionEntity>> = insightsRepo.getActivePredictions(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search for transcriptions view
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<TranscriptionWithChunk>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) analysisRepo.getAllTranscriptionsWithChunks()
            else analysisRepo.searchTranscriptionsWithChunks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadTimeBreakdown()
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        loadTimeBreakdown()
    }

    fun setViewMode(mode: String) {
        _viewMode.value = mode
        if (mode == "weekly") loadWeeklyBreakdown()
    }

    fun onSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun dismissPrediction(id: Long) {
        viewModelScope.launch { insightsRepo.dismissPrediction(id) }
    }

    fun generateDailyInsight(date: String) {
        viewModelScope.launch {
            insightGenerator.generateDailyInsight(date)
        }
    }

    fun deleteTranscription(id: Long) {
        viewModelScope.launch { analysisRepo.deleteTranscription(id) }
    }

    private fun loadTimeBreakdown() {
        viewModelScope.launch {
            _timeBreakdown.value = insightsRepo.getTimeBreakdown(_selectedDate.value)
        }
    }

    private fun loadWeeklyBreakdown() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val endDate = dateFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val startDate = dateFormat.format(cal.time)
            _weeklyTimeBreakdown.value = insightsRepo.getTimeBreakdownRange(startDate, endDate)
        }
    }

    fun formatTimestamp(epochMs: Long): String = timeFormat.format(Date(epochMs))

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val h = m / 60
        return if (h > 0) "%dh %02dm".format(h, m % 60) else "%dm %02ds".format(m, s % 60)
    }

    fun filteredTranscriptions(date: String): List<TranscriptionWithChunk> {
        return dailyTranscriptions.value.filter { it.date == date }
    }
}
