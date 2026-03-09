package com.dc.murmur.data.repository

import android.util.Base64
import com.dc.murmur.data.local.dao.CoSpeakerInfo
import com.dc.murmur.data.local.dao.SpeakerSegmentDao
import com.dc.murmur.data.local.dao.SpeakerSegmentWithDate
import com.dc.murmur.data.local.dao.SpeakerSegmentWithProfile
import com.dc.murmur.data.local.dao.VoiceProfileDao
import com.dc.murmur.data.local.entity.SpeakerSegmentEntity
import com.dc.murmur.data.local.entity.VoiceProfileEntity
import kotlinx.coroutines.flow.Flow
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SpeakerMatch(val profile: VoiceProfileEntity, val score: Float)

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

    suspend fun getSpeakerSegmentsWithProfile(chunkId: Long): List<SpeakerSegmentWithProfile> {
        return speakerSegmentDao.getByChunkWithProfile(chunkId)
    }

    suspend fun getCoSpeakers(profileId: Long): List<CoSpeakerInfo> {
        return speakerSegmentDao.getCoSpeakers(profileId)
    }

    suspend fun getUntaggedWithEmbeddings(): List<VoiceProfileEntity> {
        return voiceProfileDao.getUntaggedWithEmbeddings()
    }

    /**
     * Merge multiple profiles into one. Reassigns all speaker segments to the target profile,
     * combines interaction stats, preserves the best embedding, and deletes the source profiles.
     */
    suspend fun mergeProfiles(keepId: Long, mergeIds: List<Long>) {
        val keep = voiceProfileDao.getById(keepId) ?: return

        for (sourceId in mergeIds) {
            if (sourceId == keepId) continue
            val source = voiceProfileDao.getById(sourceId) ?: continue

            // Reassign all speaker segments to the target profile
            speakerSegmentDao.reassignSegments(sourceId, keepId)

            // Combine interaction stats
            voiceProfileDao.incrementInteraction(
                keepId,
                source.totalInteractionMs,
                maxOf(keep.lastSeenAt, source.lastSeenAt)
            )

            // If keep profile has no embedding but source does, adopt it
            if (keep.embedding == null && source.embedding != null) {
                voiceProfileDao.setEmbedding(keepId, source.embedding!!)
            }

            // Delete the merged source profile
            voiceProfileDao.deleteById(sourceId)
        }
    }

    // --- Speaker embedding matching ---

    suspend fun findSpeakerMatches(embedding: FloatArray, topK: Int = 3): List<SpeakerMatch> {
        val profilesWithEmbeddings = voiceProfileDao.getWithEmbeddings()
        if (profilesWithEmbeddings.isEmpty()) return emptyList()

        val minScore = 0.35f
        return profilesWithEmbeddings.mapNotNull { profile ->
            val storedEmbedding = profile.embedding?.let { base64ToEmbedding(it) } ?: return@mapNotNull null
            val similarity = cosineSimilarity(embedding, storedEmbedding)
            if (similarity > minScore) SpeakerMatch(profile, similarity) else null
        }.sortedByDescending { it.score }.take(topK)
    }

    suspend fun matchSpeaker(embedding: FloatArray, threshold: Float = 0.50f): VoiceProfileEntity? {
        val matches = findSpeakerMatches(embedding, topK = 1)
        val best = matches.firstOrNull() ?: return null
        return if (best.score >= threshold) best.profile else null
    }

    suspend fun enrollVoiceEmbedding(profileId: Long, embedding: FloatArray) {
        val normalized = l2Normalize(embedding)
        val base64 = embeddingToBase64(normalized)
        voiceProfileDao.updateEmbedding(profileId, base64, sampleCount = 1, updatedAt = System.currentTimeMillis())
    }

    suspend fun updateEmbeddingWithSample(profileId: Long, newEmbedding: FloatArray) {
        val profile = voiceProfileDao.getById(profileId) ?: return
        val oldEmbedding = profile.embedding?.let { base64ToEmbedding(it) }
        val count = profile.embeddingSampleCount

        val merged = if (oldEmbedding == null || count == 0) {
            newEmbedding
        } else if (count < 20) {
            // Weighted average: (old * count + new) / (count + 1)
            FloatArray(oldEmbedding.size) { i ->
                (oldEmbedding[i] * count + newEmbedding[i]) / (count + 1)
            }
        } else {
            // Exponential moving average: old * 0.9 + new * 0.1
            FloatArray(oldEmbedding.size) { i ->
                oldEmbedding[i] * 0.9f + newEmbedding[i] * 0.1f
            }
        }

        val normalized = l2Normalize(merged)
        voiceProfileDao.updateEmbedding(
            profileId,
            embeddingToBase64(normalized),
            sampleCount = count + 1,
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun l2Normalize(embedding: FloatArray): FloatArray {
            var sumSq = 0f
            for (v in embedding) sumSq += v * v
            val norm = Math.sqrt(sumSq.toDouble()).toFloat()
            if (norm < 1e-10f) return embedding
            return FloatArray(embedding.size) { embedding[it] / norm }
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dotProduct = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denominator = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
            return if (denominator == 0.0) 0f else (dotProduct / denominator).toFloat()
        }

        fun embeddingToBase64(embedding: FloatArray): String {
            val buffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            embedding.forEach { buffer.putFloat(it) }
            return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        }

        fun base64ToEmbedding(str: String): FloatArray {
            val bytes = Base64.decode(str, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) {
                floats[i] = buffer.getFloat()
            }
            return floats
        }

        fun embeddingHash(embedding: FloatArray): String {
            // Stable hash for use as voiceId when creating new profiles from embeddings
            val buffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            embedding.forEach { buffer.putFloat(it) }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(buffer.array())
            return hash.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
