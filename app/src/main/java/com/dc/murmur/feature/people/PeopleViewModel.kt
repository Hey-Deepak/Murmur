package com.dc.murmur.feature.people

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.ai.rust.RustPipeline
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.local.dao.CoSpeakerInfo
import com.dc.murmur.data.local.dao.SpeakerSegmentWithDate
import com.dc.murmur.data.local.entity.TopicEntity
import com.dc.murmur.data.local.entity.VoiceProfileEntity
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.SpeakerMatch
import com.dc.murmur.feature.recordings.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeopleViewModel(
    private val peopleRepo: PeopleRepository,
    private val insightsRepo: InsightsRepository,
    private val rustPipeline: RustPipeline,
    private val chunkDao: RecordingChunkDao
) : ViewModel() {

    val audioPlayer = AudioPlayer()

    val taggedProfiles: StateFlow<List<VoiceProfileEntity>> = peopleRepo.getTaggedProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val untaggedProfiles: StateFlow<List<VoiceProfileEntity>> = peopleRepo.getUntaggedProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProfile = MutableStateFlow<VoiceProfileEntity?>(null)
    val selectedProfile: StateFlow<VoiceProfileEntity?> = _selectedProfile.asStateFlow()

    private val _selectedProfileSegments = MutableStateFlow<List<SpeakerSegmentWithDate>>(emptyList())
    val selectedProfileSegments: StateFlow<List<SpeakerSegmentWithDate>> = _selectedProfileSegments.asStateFlow()

    private val _selectedProfileTopics = MutableStateFlow<List<TopicEntity>>(emptyList())
    val selectedProfileTopics: StateFlow<List<TopicEntity>> = _selectedProfileTopics.asStateFlow()

    // Match suggestions: profileId → top matches from tagged profiles
    private val _matchSuggestions = MutableStateFlow<Map<Long, List<SpeakerMatch>>>(emptyMap())
    val matchSuggestions: StateFlow<Map<Long, List<SpeakerMatch>>> = _matchSuggestions.asStateFlow()

    // Co-speaker info: profileId → list of co-speakers
    private val _coSpeakers = MutableStateFlow<Map<Long, List<CoSpeakerInfo>>>(emptyMap())
    val coSpeakers: StateFlow<Map<Long, List<CoSpeakerInfo>>> = _coSpeakers.asStateFlow()

    init {
        // Recompute suggestions whenever untagged profiles change
        viewModelScope.launch {
            untaggedProfiles.collect { profiles ->
                loadMatchSuggestions(profiles)
            }
        }
    }

    private fun loadMatchSuggestions(untagged: List<VoiceProfileEntity>) {
        viewModelScope.launch(Dispatchers.Default) {
            val suggestions = mutableMapOf<Long, List<SpeakerMatch>>()
            val coSpeakerMap = mutableMapOf<Long, List<CoSpeakerInfo>>()
            for (profile in untagged) {
                val embedding = profile.embedding?.let { PeopleRepository.base64ToEmbedding(it) }
                if (embedding != null) {
                    val matches = peopleRepo.findSpeakerMatches(embedding, topK = 3)
                    // Only show suggestions from tagged profiles (the user has named them)
                    val taggedMatches = matches.filter { it.profile.label != null && it.profile.id != profile.id }
                    if (taggedMatches.isNotEmpty()) {
                        suggestions[profile.id] = taggedMatches
                    }
                }
                // Load co-speakers
                val cos = peopleRepo.getCoSpeakers(profile.id)
                    .filter { it.label != null }  // Only show named co-speakers
                if (cos.isNotEmpty()) {
                    coSpeakerMap[profile.id] = cos
                }
            }
            _matchSuggestions.value = suggestions
            _coSpeakers.value = coSpeakerMap
        }
    }

    // Merge mode state
    private val _mergeMode = MutableStateFlow(false)
    val mergeMode: StateFlow<Boolean> = _mergeMode.asStateFlow()

    private val _mergeSelection = MutableStateFlow<Set<Long>>(emptySet())
    val mergeSelection: StateFlow<Set<Long>> = _mergeSelection.asStateFlow()

    fun selectProfile(id: Long) {
        viewModelScope.launch {
            _selectedProfile.value = peopleRepo.getProfileById(id)
            _selectedProfileSegments.value = peopleRepo.getRecentInteractions(id, 20)
        }
    }

    fun clearSelection() {
        _selectedProfile.value = null
        _selectedProfileSegments.value = emptyList()
        _selectedProfileTopics.value = emptyList()
        audioPlayer.release()
    }

    fun tagProfile(profileId: Long, name: String) {
        viewModelScope.launch {
            peopleRepo.tagProfile(profileId, name)
            // Refresh selected profile
            _selectedProfile.value = peopleRepo.getProfileById(profileId)
        }
    }

    fun setProfilePhoto(profileId: Long, uri: String) {
        viewModelScope.launch {
            peopleRepo.setProfilePhoto(profileId, uri)
            _selectedProfile.value = peopleRepo.getProfileById(profileId)
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            peopleRepo.deleteProfile(profileId)
            if (_selectedProfile.value?.id == profileId) {
                clearSelection()
            }
        }
    }

    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val h = m / 60
        return if (h > 0) "%dh %02dm".format(h, m % 60) else "%dm %02ds".format(m, s % 60)
    }

    fun formatTimeSince(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        val hours = diff / 3600000
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            else -> "just now"
        }
    }

    // --- Voice enrollment ---

    sealed class EnrollmentState {
        data object Idle : EnrollmentState()
        data class Recording(val elapsedSeconds: Int) : EnrollmentState()
        data object Processing : EnrollmentState()
        data object Success : EnrollmentState()
        data class Error(val message: String) : EnrollmentState()
    }

    private val _enrollmentState = MutableStateFlow<EnrollmentState>(EnrollmentState.Idle)
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var isRecordingVoice = false

    companion object {
        private const val TAG = "PeopleViewModel"
        private const val SAMPLE_RATE = 16000
        private const val ENROLLMENT_DURATION_SECONDS = 15
    }

    fun startVoiceEnrollment(profileId: Long) {
        if (isRecordingVoice) return

        viewModelScope.launch {
            try {
                isRecordingVoice = true
                _enrollmentState.value = EnrollmentState.Recording(0)

                val pcmData = withContext(Dispatchers.IO) { recordAudio() }

                _enrollmentState.value = EnrollmentState.Processing

                // Convert 16-bit PCM bytes to float and extract embedding via Rust pipeline
                val pcmFloat = rustPipeline.pcmBytesToFloat(pcmData)
                val embeddingBase64 = withContext(Dispatchers.IO) {
                    rustPipeline.extractEmbedding(pcmFloat)
                }

                if (embeddingBase64 != null) {
                    val embedding = PeopleRepository.base64ToEmbedding(embeddingBase64)
                    peopleRepo.enrollVoiceEmbedding(profileId, embedding)
                    _enrollmentState.value = EnrollmentState.Success
                    // Refresh selected profile
                    _selectedProfile.value = peopleRepo.getProfileById(profileId)
                    Log.i(TAG, "Voice enrolled for profile $profileId (dim=${embedding.size})")
                } else {
                    _enrollmentState.value = EnrollmentState.Error("Could not extract voice embedding. Try speaking longer.")
                }
            } catch (e: SecurityException) {
                _enrollmentState.value = EnrollmentState.Error("Microphone permission required")
            } catch (e: Exception) {
                Log.e(TAG, "Enrollment failed: ${e.message}", e)
                _enrollmentState.value = EnrollmentState.Error(e.message ?: "Enrollment failed")
            } finally {
                isRecordingVoice = false
            }
        }
    }

    fun stopVoiceEnrollment() {
        isRecordingVoice = false
        audioRecord?.stop()
    }

    fun resetEnrollmentState() {
        _enrollmentState.value = EnrollmentState.Idle
    }

    private fun recordAudio(): ByteArray {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        audioRecord = record
        record.startRecording()

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val totalBytes = SAMPLE_RATE * 2 * ENROLLMENT_DURATION_SECONDS // 16-bit = 2 bytes per sample
        var bytesRecorded = 0

        try {
            while (isRecordingVoice && bytesRecorded < totalBytes) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    bytesRecorded += read
                    val elapsedSeconds = bytesRecorded / (SAMPLE_RATE * 2)
                    _enrollmentState.value = EnrollmentState.Recording(elapsedSeconds)
                }
            }
        } finally {
            record.stop()
            record.release()
            audioRecord = null
        }

        return outputStream.toByteArray()
    }

    fun hasVoiceEmbedding(profile: VoiceProfileEntity): Boolean {
        return profile.embedding != null
    }

    // --- Audio playback for interactions ---

    /** Tracks which profile ID is currently being played (for icon state) */
    private val _playingProfileId = MutableStateFlow<Long?>(null)
    val playingProfileId: StateFlow<Long?> = _playingProfileId.asStateFlow()

    /**
     * Play a preview of the latest interaction for a voice profile.
     * Plays only this speaker's audio segments so the user can identify the voice.
     * Falls back to full chunk if no segment timings are available.
     */
    fun playLatestInteraction(profileId: Long) {
        // If already playing this profile, stop
        if (_playingProfileId.value == profileId && audioPlayer.isPlaying.value) {
            stopPlayback()
            return
        }

        viewModelScope.launch {
            val interactions = peopleRepo.getRecentInteractions(profileId, 1)
            if (interactions.isEmpty()) return@launch

            val chunkId = interactions.first().chunkId
            // Delegate to speaker-specific playback
            playSpeakerAudio(chunkId, profileId)
        }
    }

    fun playChunkAudio(chunkId: Long) {
        viewModelScope.launch {
            val chunk = chunkDao.getById(chunkId) ?: return@launch
            val file = java.io.File(chunk.filePath)
            if (file.exists()) {
                audioPlayer.togglePlayPause(chunk.filePath)
            }
        }
    }

    /**
     * Play only the portions of a chunk where a specific speaker talks.
     * Uses segmentTimings JSON from the speaker segment to seek to the right parts.
     * Falls back to full chunk only when no timing data is available.
     */
    fun playSpeakerAudio(chunkId: Long, profileId: Long) {
        // If already playing this profile, stop
        if (_playingProfileId.value == profileId && audioPlayer.isPlaying.value) {
            stopPlayback()
            return
        }

        viewModelScope.launch {
            val chunk = chunkDao.getById(chunkId) ?: return@launch
            val file = java.io.File(chunk.filePath)
            if (!file.exists()) return@launch

            _playingProfileId.value = profileId

            // Find the speaker segment with timings for this chunk + profile
            val segments = peopleRepo.getSegmentsForChunk(chunkId)
            val speakerSegment = segments.find { it.voiceProfileId == profileId }
            val timings = speakerSegment?.segmentTimings?.let { parseTimingsJson(it) }

            if (timings != null && timings.isNotEmpty()) {
                audioPlayer.playSpeakerSegments(chunk.filePath, timings)
            } else {
                // No timing data — play full chunk
                audioPlayer.play(chunk.filePath)
            }

            // Monitor for playback completion to reset icon
            viewModelScope.launch {
                audioPlayer.isPlaying.collect { playing ->
                    if (!playing && _playingProfileId.value == profileId) {
                        _playingProfileId.value = null
                    }
                }
            }
        }
    }

    private fun parseTimingsJson(json: String): List<Pair<Long, Long>> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val pair = arr.getJSONArray(i)
                pair.getLong(0) to pair.getLong(1)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun stopPlayback() {
        _playingProfileId.value = null
        audioPlayer.release()
    }

    // --- Profile merging ---

    fun toggleMergeMode() {
        _mergeMode.value = !_mergeMode.value
        if (!_mergeMode.value) {
            _mergeSelection.value = emptySet()
        }
    }

    fun toggleMergeSelection(profileId: Long) {
        val current = _mergeSelection.value.toMutableSet()
        if (current.contains(profileId)) current.remove(profileId) else current.add(profileId)
        _mergeSelection.value = current
    }

    fun mergeProfilePair(keepId: Long, mergeId: Long) {
        viewModelScope.launch {
            peopleRepo.mergeProfiles(keepId, listOf(mergeId))
            Log.i(TAG, "Merged profile $mergeId into $keepId")
        }
    }

    fun executeMerge() {
        val selected = _mergeSelection.value.toList()
        if (selected.size < 2) return

        viewModelScope.launch {
            // Keep the first selected profile (or the one with a label)
            val profiles = selected.mapNotNull { peopleRepo.getProfileById(it) }
            val keepProfile = profiles.firstOrNull { it.label != null }
                ?: profiles.firstOrNull { it.embedding != null }
                ?: profiles.first()

            val mergeIds = selected.filter { it != keepProfile.id }
            peopleRepo.mergeProfiles(keepProfile.id, mergeIds)

            _mergeMode.value = false
            _mergeSelection.value = emptySet()
            Log.i(TAG, "Merged ${mergeIds.size} profiles into ${keepProfile.id} (${keepProfile.label ?: keepProfile.voiceId})")
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.destroy()
    }
}
