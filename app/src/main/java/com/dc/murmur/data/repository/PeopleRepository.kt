package com.dc.murmur.data.repository

import com.dc.murmur.data.local.dao.SpeakerSegmentDao
import com.dc.murmur.data.local.dao.SpeakerSegmentWithDate
import com.dc.murmur.data.local.dao.VoiceProfileDao
import com.dc.murmur.data.local.entity.SpeakerSegmentEntity
import com.dc.murmur.data.local.entity.VoiceProfileEntity
import kotlinx.coroutines.flow.Flow

class PeopleRepository(
    private val voiceProfileDao: VoiceProfileDao,
    private val speakerSegmentDao: SpeakerSegmentDao
) {

    // Profiles
    fun getAllProfiles(): Flow<List<VoiceProfileEntity>> {
        return voiceProfileDao.getAll()
    }

    fun getTaggedProfiles(): Flow<List<VoiceProfileEntity>> {
        return voiceProfileDao.getTagged()
    }

    fun getUntaggedProfiles(): Flow<List<VoiceProfileEntity>> {
        return voiceProfileDao.getUntagged()
    }

    suspend fun getOrCreateProfile(voiceId: String, now: Long): VoiceProfileEntity {
        val existing = voiceProfileDao.getByVoiceId(voiceId)
        if (existing != null) return existing
        val profile = VoiceProfileEntity(
            voiceId = voiceId,
            firstSeenAt = now,
            lastSeenAt = now
        )
        val id = voiceProfileDao.insert(profile)
        return profile.copy(id = id)
    }

    suspend fun tagProfile(profileId: Long, name: String) {
        voiceProfileDao.setLabel(profileId, name)
    }

    suspend fun setProfilePhoto(profileId: Long, uri: String) {
        voiceProfileDao.setPhoto(profileId, uri)
    }

    suspend fun deleteProfile(profileId: Long) {
        voiceProfileDao.deleteById(profileId)
    }

    suspend fun getProfileById(id: Long): VoiceProfileEntity? {
        return voiceProfileDao.getById(id)
    }

    suspend fun incrementInteraction(profileId: Long, addMs: Long, lastSeen: Long) {
        voiceProfileDao.incrementInteraction(profileId, addMs, lastSeen)
    }

    // Segments
    suspend fun saveSpeakerSegments(segments: List<SpeakerSegmentEntity>) {
        if (segments.isNotEmpty()) {
            speakerSegmentDao.insertAll(segments)
        }
    }

    suspend fun getSegmentsForChunk(chunkId: Long): List<SpeakerSegmentEntity> {
        return speakerSegmentDao.getByChunk(chunkId)
    }

    fun getSegmentsForProfile(profileId: Long): Flow<List<SpeakerSegmentEntity>> {
        return speakerSegmentDao.getByProfile(profileId)
    }

    suspend fun getRecentInteractions(profileId: Long, limit: Int = 20): List<SpeakerSegmentWithDate> {
        return speakerSegmentDao.getRecentByProfile(profileId, limit)
    }

    suspend fun getTotalSpeakingTime(profileId: Long): Long {
        return speakerSegmentDao.getTotalSpeakingTime(profileId)
    }
}
