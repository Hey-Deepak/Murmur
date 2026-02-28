package com.dc.murmur.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.data.local.dao.SpeakerSegmentWithDate
import com.dc.murmur.data.local.entity.TopicEntity
import com.dc.murmur.data.local.entity.VoiceProfileEntity
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(
    private val peopleRepo: PeopleRepository,
    private val insightsRepo: InsightsRepository
) : ViewModel() {

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
}
