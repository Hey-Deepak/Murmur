package com.dc.murmur.feature.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.ai.AnalysisStateHolder
import com.dc.murmur.ai.AnalysisUiState
import com.dc.murmur.ai.AnalysisWorker
import com.dc.murmur.core.constants.AppConstants
import com.dc.murmur.data.local.MurmurDatabase
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.local.dao.SpeakerSegmentWithProfile
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import com.dc.murmur.data.local.entity.TranscriptionEntity
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.BatteryRepository
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.RecordingRepository
import com.dc.murmur.data.repository.SettingsRepository
import com.dc.murmur.feature.recordings.AudioPlayer
import com.dc.murmur.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class HomeViewModel(
    private val recordingRepo: RecordingRepository,
    private val batteryRepo: BatteryRepository,
    private val settingsRepo: SettingsRepository,
    private val analysisRepo: AnalysisRepository,
    private val analysisState: AnalysisStateHolder,
    private val database: MurmurDatabase,
    private val peopleRepo: PeopleRepository,
    private val chunkDao: RecordingChunkDao
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

    // Speaker data per chunk
    private val _chunkSpeakers = MutableStateFlow<Map<Long, List<SpeakerSegmentWithProfile>>>(emptyMap())
    val chunkSpeakers: StateFlow<Map<Long, List<SpeakerSegmentWithProfile>>> = _chunkSpeakers.asStateFlow()

    private val _chunkFilePaths = MutableStateFlow<Map<Long, String>>(emptyMap())

    val taggedProfileNames: StateFlow<List<String>> = peopleRepo.getTaggedProfiles()
        .map { profiles -> profiles.mapNotNull { it.label }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioPlayer = AudioPlayer()

    private val _playingProfileId = MutableStateFlow<Long?>(null)
    val playingProfileId: StateFlow<Long?> = _playingProfileId.asStateFlow()

    init {
        // Load speakers whenever transcriptions change
        viewModelScope.launch {
            recentTranscriptions.collect { transcriptions ->
                loadSpeakersForChunks(transcriptions)
            }
        }
    }

    private fun loadSpeakersForChunks(transcriptions: List<TranscriptionEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val speakerMap = mutableMapOf<Long, List<SpeakerSegmentWithProfile>>()
            val filePathMap = mutableMapOf<Long, String>()
            for (t in transcriptions) {
                val speakers = peopleRepo.getSpeakerSegmentsWithProfile(t.chunkId)
                if (speakers.isNotEmpty()) {
                    speakerMap[t.chunkId] = speakers
                }
                val chunk = chunkDao.getById(t.chunkId)
                if (chunk != null) {
                    filePathMap[t.chunkId] = chunk.filePath
                }
            }
            _chunkSpeakers.value = speakerMap
            _chunkFilePaths.value = filePathMap
        }
    }

    fun playSpeaker(chunkId: Long, profileId: Long) {
        // Toggle off if already playing this speaker
        if (_playingProfileId.value == profileId && audioPlayer.isPlaying.value) {
            stopPlayback()
            return
        }

        viewModelScope.launch {
            val filePath = _chunkFilePaths.value[chunkId] ?: return@launch
            val file = java.io.File(filePath)
            if (!file.exists()) return@launch

            _playingProfileId.value = profileId

            val segments = peopleRepo.getSegmentsForChunk(chunkId)
            val speakerSegment = segments.find { it.voiceProfileId == profileId }
            val timings = speakerSegment?.segmentTimings?.let { parseTimingsJson(it) }

            if (timings != null && timings.isNotEmpty()) {
                audioPlayer.playSpeakerSegments(filePath, timings)
            } else {
                audioPlayer.play(filePath)
            }

            // Monitor for playback completion
            viewModelScope.launch {
                audioPlayer.isPlaying.collect { playing ->
                    if (!playing && _playingProfileId.value == profileId) {
                        _playingProfileId.value = null
                    }
                }
            }
        }
    }

    fun stopPlayback() {
        _playingProfileId.value = null
        audioPlayer.release()
    }

    fun tagSpeaker(profileId: Long, name: String) {
        viewModelScope.launch {
            peopleRepo.tagProfile(profileId, name)
            // Reload speakers to pick up the new label
            loadSpeakersForChunks(recentTranscriptions.value)
        }
    }

    private fun parseTimingsJson(json: String): List<Pair<Long, Long>> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val pair = arr.getJSONArray(i)
                pair.getLong(0) to pair.getLong(1)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

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

    fun resetAllData(context: Context) {
        // Stop recording and analysis on main thread first
        stopRecording(context)
        cancelAnalysis(context)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Nuke the entire database
                database.clearAllTables()
                // Delete all audio files
                if (AppConstants.RECORDINGS_DIR.exists()) {
                    AppConstants.RECORDINGS_DIR.deleteRecursively()
                    AppConstants.RECORDINGS_DIR.mkdirs()
                }
            }
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

    override fun onCleared() {
        super.onCleared()
        audioPlayer.destroy()
    }
}
