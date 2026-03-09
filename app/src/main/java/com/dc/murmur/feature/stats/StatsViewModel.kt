package com.dc.murmur.feature.stats

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.ai.AnalysisStateHolder
import com.dc.murmur.ai.AnalysisUiState
import com.dc.murmur.ai.AnalysisWorker
import com.dc.murmur.ai.BridgeStatus
import com.dc.murmur.ai.BridgeStatusHolder
import com.dc.murmur.ai.BridgeUiState
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.core.util.TermuxBridgeManager
import com.dc.murmur.data.local.dao.ActivityTimeBreakdown
import com.dc.murmur.data.local.entity.BatteryLogEntity
import com.dc.murmur.data.local.entity.TopicEntity
import com.dc.murmur.data.local.entity.VoiceProfileEntity
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.BatteryRepository
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.RecordingRepository
import com.dc.murmur.data.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(
    private val recordingRepo: RecordingRepository,
    private val batteryRepo: BatteryRepository,
    private val settingsRepo: SettingsRepository,
    private val analysisRepo: AnalysisRepository,
    private val analysisState: AnalysisStateHolder,
    private val claudeAnalyzer: ClaudeCodeAnalyzer,
    private val bridgeManager: TermuxBridgeManager,
    private val bridgeStatusHolder: BridgeStatusHolder,
    private val insightsRepo: InsightsRepository,
    private val peopleRepo: PeopleRepository
) : ViewModel() {

    // --- Trend data ---
    private val _weeklyActivityTrend = MutableStateFlow<Map<String, List<ActivityTimeBreakdown>>>(emptyMap())
    val weeklyActivityTrend: StateFlow<Map<String, List<ActivityTimeBreakdown>>> = _weeklyActivityTrend.asStateFlow()

    private val _monthlySentimentTrend = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val monthlySentimentTrend: StateFlow<List<Pair<String, Float>>> = _monthlySentimentTrend.asStateFlow()

    val topPeopleThisWeek: StateFlow<List<VoiceProfileEntity>> = peopleRepo.getTaggedProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topTopicsThisWeek: StateFlow<List<TopicEntity>> = insightsRepo.getTopTopics(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _predictionAccuracy = MutableStateFlow(0f)
    val predictionAccuracy: StateFlow<Float> = _predictionAccuracy.asStateFlow()

    val totalStorageBytes: StateFlow<Long> = recordingRepo.getTotalStorageBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val audioQuality: StateFlow<String> = settingsRepo.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "normal")

    val autoDeleteDays: StateFlow<Int> = settingsRepo.autoDeleteDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 14)

    val autoStartOnBoot: StateFlow<Boolean> = settingsRepo.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val analysisEnabled: StateFlow<Boolean> = settingsRepo.analysisEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val requireCharging: StateFlow<Boolean> = settingsRepo.requireCharging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val minBattery: StateFlow<Int> = settingsRepo.minBattery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)

    val analysisMode: StateFlow<String> = settingsRepo.analysisMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fixed_time")

    val analysisHour: StateFlow<Int> = settingsRepo.analysisHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22)

    val analysisMinute: StateFlow<Int> = settingsRepo.analysisMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val analysisDays: StateFlow<Set<String>> = settingsRepo.analysisDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), setOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun"))

    val todayBatteryLogs: StateFlow<List<BatteryLogEntity>> = batteryRepo.getTodayLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analysisUiState: StateFlow<AnalysisUiState> = analysisState.state

    val analysisLog: StateFlow<List<String>> = analysisState.logEntries

    val unprocessedCount: StateFlow<Int> = analysisRepo.getUnprocessedCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startAnalysis(context: Context) {
        analysisState.setIdle()
        AnalysisWorker.enqueueNow(context)
    }

    fun cancelAnalysis(context: Context) {
        AnalysisWorker.cancel(context)
        analysisState.setIdle()
    }

    fun setAudioQuality(q: String) = viewModelScope.launch { settingsRepo.setAudioQuality(q) }
    fun setAutoDeleteDays(days: Int) = viewModelScope.launch { settingsRepo.setAutoDeleteDays(days) }
    fun setAutoStartOnBoot(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoStartOnBoot(v) }
    fun setAnalysisEnabled(v: Boolean) = viewModelScope.launch { settingsRepo.setAnalysisEnabled(v) }
    fun setRequireCharging(v: Boolean) = viewModelScope.launch { settingsRepo.setRequireCharging(v) }
    fun setAnalysisMode(mode: String) = viewModelScope.launch { settingsRepo.setAnalysisMode(mode) }
    fun setMinBattery(level: Int) = viewModelScope.launch { settingsRepo.setMinBattery(level) }
    fun setAnalysisTime(hour: Int, minute: Int) = viewModelScope.launch { settingsRepo.setAnalysisTime(hour, minute) }
    fun setAnalysisDays(days: Set<String>) = viewModelScope.launch { settingsRepo.setAnalysisDays(days) }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000.0)
        else                   -> "%.0f KB".format(bytes / 1_000.0)
    }

    // --- Claude Bridge ---

    val bridgeUiState: StateFlow<BridgeUiState> = bridgeStatusHolder.state

    val claudeBridgePort: StateFlow<Int> = settingsRepo.claudeBridgePort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8735)

    val claudeBridgeAutoStart: StateFlow<Boolean> = settingsRepo.claudeBridgeAutoStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isTermuxInstalled = MutableStateFlow(bridgeManager.isTermuxInstalled())
    val isTermuxInstalled: StateFlow<Boolean> = _isTermuxInstalled.asStateFlow()

    init {
        checkBridgeStatus()
        loadTrends()
    }

    private fun loadTrends() {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            val today = dateFormat.format(cal.time)

            // Weekly activity trend: per-day breakdown for past 7 days
            val weeklyMap = mutableMapOf<String, List<ActivityTimeBreakdown>>()
            for (i in 6 downTo 0) {
                val c = Calendar.getInstance()
                c.add(Calendar.DAY_OF_YEAR, -i)
                val date = dateFormat.format(c.time)
                val breakdown = insightsRepo.getTimeBreakdown(date)
                if (breakdown.isNotEmpty()) {
                    weeklyMap[date] = breakdown
                }
            }
            _weeklyActivityTrend.value = weeklyMap

            // Monthly sentiment trend
            val sentiments = mutableListOf<Pair<String, Float>>()
            for (i in 29 downTo 0) {
                val c = Calendar.getInstance()
                c.add(Calendar.DAY_OF_YEAR, -i)
                val date = dateFormat.format(c.time)
                val insight = insightsRepo.getDailyInsight(date)
                if (insight != null) {
                    sentiments.add(date to insight.overallSentimentScore)
                }
            }
            _monthlySentimentTrend.value = sentiments
        }
    }

    fun checkBridgeStatus() {
        viewModelScope.launch {
            try {
                val available = claudeAnalyzer.isAvailable()
                if (available) {
                    bridgeStatusHolder.setRunning()
                } else {
                    bridgeStatusHolder.setStopped()
                }
            } catch (e: Exception) {
                bridgeStatusHolder.setStopped()
            }
            _isTermuxInstalled.value = bridgeManager.isTermuxInstalled()
        }
    }

    fun startBridge() {
        viewModelScope.launch {
            bridgeStatusHolder.setStarting()
            val port = settingsRepo.getClaudeBridgePort()
            val result = bridgeManager.startBridge(port)
            when (result) {
                TermuxBridgeManager.Result.COMMAND_SENT -> pollBridgeHealth(expectRunning = true)
                TermuxBridgeManager.Result.TERMUX_OPENED -> pollBridgeHealth(expectRunning = true, maxAttempts = 30)
                TermuxBridgeManager.Result.FAILED -> bridgeStatusHolder.setError("Could not reach Termux")
            }
        }
    }

    fun stopBridge() {
        viewModelScope.launch {
            bridgeStatusHolder.setStopping()
            val result = bridgeManager.stopBridge()
            when (result) {
                TermuxBridgeManager.Result.COMMAND_SENT -> pollBridgeHealth(expectRunning = false)
                TermuxBridgeManager.Result.TERMUX_OPENED -> pollBridgeHealth(expectRunning = false, maxAttempts = 30)
                TermuxBridgeManager.Result.FAILED -> bridgeStatusHolder.setError("Could not reach Termux")
            }
        }
    }

    fun installBridgeScripts() {
        bridgeManager.installScripts()
    }

    fun setClaudeBridgePort(port: Int) = viewModelScope.launch {
        settingsRepo.setClaudeBridgePort(port)
    }

    fun setClaudeBridgeAutoStart(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setClaudeBridgeAutoStart(enabled)
    }

    private suspend fun pollBridgeHealth(expectRunning: Boolean, maxAttempts: Int = 10) {
        repeat(maxAttempts) {
            delay(1_000)
            try {
                val available = claudeAnalyzer.isAvailable()
                if (expectRunning && available) {
                    bridgeStatusHolder.setRunning()
                    return
                }
                if (!expectRunning && !available) {
                    bridgeStatusHolder.setStopped()
                    return
                }
            } catch (_: Exception) {
                if (!expectRunning) {
                    bridgeStatusHolder.setStopped()
                    return
                }
            }
        }
        // Timeout — set based on what we expected
        if (expectRunning) {
            bridgeStatusHolder.setError("Bridge did not start within ${maxAttempts}s")
        } else {
            bridgeStatusHolder.setError("Bridge did not stop within ${maxAttempts}s")
        }
    }
}
