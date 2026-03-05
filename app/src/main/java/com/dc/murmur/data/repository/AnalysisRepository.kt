package com.dc.murmur.data.repository

import android.util.Log
import com.dc.murmur.ai.AnalysisResult
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.local.dao.TranscriptionDao
import com.dc.murmur.data.local.entity.ActivityEntity
import com.dc.murmur.data.local.entity.SpeakerSegmentEntity
import com.dc.murmur.data.local.entity.TranscriptionEntity
import com.dc.murmur.data.local.entity.TranscriptionWithChunk
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class AnalysisRepository(
    private val transcriptionDao: TranscriptionDao,
    private val chunkDao: RecordingChunkDao
) {

    private var insightsRepository: InsightsRepository? = null
    private var peopleRepository: PeopleRepository? = null

    fun setInsightsRepository(repo: InsightsRepository) {
        insightsRepository = repo
    }

    fun setPeopleRepository(repo: PeopleRepository) {
        peopleRepository = repo
    }

    suspend fun saveTranscription(result: AnalysisResult) {
        val entity = TranscriptionEntity(
            chunkId = result.chunkId,
            text = result.text,
            sentiment = result.sentiment,
            sentimentScore = result.sentimentScore,
            keywords = result.keywords,
            processedAt = System.currentTimeMillis(),
            modelUsed = result.modelUsed
        )
        transcriptionDao.insert(entity)
        chunkDao.markProcessed(result.chunkId)
    }

    suspend fun saveFullAnalysis(
        result: AnalysisResult,
        chunkDate: String,
        chunkStartTime: Long,
        chunkDurationMs: Long
    ) {
        val now = System.currentTimeMillis()

        // 1. Save expanded TranscriptionEntity
        val topicNames = result.topics.map { it.name }
        val entity = TranscriptionEntity(
            chunkId = result.chunkId,
            text = result.text,
            sentiment = result.sentiment,
            sentimentScore = result.sentimentScore,
            keywords = result.keywords,
            processedAt = now,
            modelUsed = result.modelUsed,
            activityType = result.activityType,
            speakerCount = result.speakers.size.takeIf { it > 0 },
            topicsSummary = if (topicNames.isNotEmpty()) JSONArray(topicNames).toString() else null,
            behavioralTags = if (result.behavioralTags.isNotEmpty()) JSONArray(result.behavioralTags).toString() else null,
            keyMoment = result.keyMoment,
            analysisVersion = 2
        )
        transcriptionDao.insert(entity)

        // 2. Save ActivityEntity
        val insights = insightsRepository
        if (result.activityType != null && insights != null) {
            val activity = ActivityEntity(
                chunkId = result.chunkId,
                activityType = result.activityType,
                confidence = result.activityConfidence ?: 0.5f,
                subActivity = result.activitySubType,
                date = chunkDate,
                startTime = chunkStartTime,
                durationMs = chunkDurationMs,
                detectedAt = now
            )
            insights.saveActivity(activity)
        }

        // 3. Save speaker segments + voice profiles
        val people = peopleRepository
        if (result.speakers.isNotEmpty() && people != null) {
            val segments = result.speakers.mapIndexed { index, speaker ->
                // Use diarization data for stable identity across chunks
                val diarized = result.diarizedSpeakers.getOrNull(index)

                val profile = if (speaker.matchedProfileId != null) {
                    // Speaker matched to existing profile via embedding — refine with new sample
                    val matched = people.getProfileById(speaker.matchedProfileId)
                        ?: people.getOrCreateProfile(voiceId = "matched_${speaker.matchedProfileId}", now = now)
                    if (diarized?.embedding != null && diarized.embedding.isNotEmpty()) {
                        people.updateEmbeddingWithSample(matched.id, diarized.embedding)
                    }
                    matched
                } else if (diarized?.embedding != null && diarized.embedding.isNotEmpty()) {
                    // Unmatched but have embedding — use embedding hash for stable voiceId
                    val voiceId = "emb_${PeopleRepository.embeddingHash(diarized.embedding)}"
                    val profile = people.getOrCreateProfile(voiceId = voiceId, now = now)
                    // Store the embedding for future matching (initial enrollment)
                    if (profile.embedding == null) {
                        people.enrollVoiceEmbedding(profile.id, diarized.embedding)
                    } else {
                        // Accumulate sample into existing profile
                        people.updateEmbeddingWithSample(profile.id, diarized.embedding)
                    }
                    profile
                } else {
                    // Fallback: no diarization data, use chunk-based ID
                    people.getOrCreateProfile(
                        voiceId = "chunk_${result.chunkId}_${speaker.label}",
                        now = now
                    )
                }

                people.incrementInteraction(
                    profileId = profile.id,
                    addMs = (speaker.speakingRatio * chunkDurationMs).toLong(),
                    lastSeen = now
                )
                // Build segment timings JSON from diarization data
                val timingsJson = diarized?.timings?.takeIf { it.isNotEmpty() }?.let { timings ->
                    val arr = JSONArray()
                    timings.forEach { (start, end) ->
                        val pair = JSONArray()
                        pair.put(start)
                        pair.put(end)
                        arr.put(pair)
                    }
                    arr.toString()
                }

                SpeakerSegmentEntity(
                    chunkId = result.chunkId,
                    voiceProfileId = profile.id,
                    speakerLabel = speaker.label,
                    speakingDurationMs = (speaker.speakingRatio * chunkDurationMs).toLong(),
                    turnCount = speaker.turnCount,
                    role = speaker.role,
                    emotionalState = speaker.emotionalState,
                    segmentTimings = timingsJson
                )
            }
            people.saveSpeakerSegments(segments)

            // Auto-merge high-confidence untagged duplicates
            autoMergeUntaggedDuplicates(people)
        }

        // 4. Save topics + chunk-topic junctions
        if (result.topics.isNotEmpty() && insights != null) {
            for (topicResult in result.topics) {
                val topic = insights.getOrCreateTopic(
                    name = topicResult.name,
                    category = topicResult.category,
                    now = now
                )
                val keyPointsJson = if (topicResult.keyPoints.isNotEmpty()) {
                    JSONArray(topicResult.keyPoints).toString()
                } else null
                insights.linkChunkToTopic(
                    chunkId = result.chunkId,
                    topicId = topic.id,
                    relevance = topicResult.relevance,
                    keyPoints = keyPointsJson
                )
            }
        }

        // 5. Mark chunk as processed
        chunkDao.markProcessed(result.chunkId)
    }

    fun getRecentTranscriptions(limit: Int = 5): Flow<List<TranscriptionEntity>> {
        return transcriptionDao.getRecent(limit)
    }

    suspend fun getUnprocessedCount(): Int {
        return chunkDao.getUnprocessedCount()
    }

    fun getUnprocessedCountFlow(): Flow<Int> {
        return chunkDao.getUnprocessedCountFlow()
    }

    fun getAllTranscriptions(): Flow<List<TranscriptionEntity>> {
        return transcriptionDao.getAll()
    }

    suspend fun deleteTranscription(id: Long) {
        transcriptionDao.deleteById(id)
    }

    suspend fun clearAllTranscriptions() {
        transcriptionDao.deleteAll()
    }

    suspend fun markAllForReanalysis() {
        transcriptionDao.deleteAll()
        chunkDao.resetAllProcessed()
    }

    fun getAllTranscriptionsWithChunks(): Flow<List<TranscriptionWithChunk>> {
        return transcriptionDao.getAllWithChunks()
    }

    fun searchTranscriptionsWithChunks(query: String): Flow<List<TranscriptionWithChunk>> {
        return transcriptionDao.searchWithChunks(query)
    }

    suspend fun getTranscriptionForChunk(chunkId: Long): TranscriptionWithChunk? {
        return transcriptionDao.getWithChunkByChunkId(chunkId)
    }

    private suspend fun autoMergeUntaggedDuplicates(people: PeopleRepository) {
        try {
            // 1. Auto-tag: match untagged profiles against tagged ones
            autoTagFromVoiceMatch(people)

            // 2. Auto-merge untagged duplicates
            val untaggedWithEmbeddings = people.getUntaggedWithEmbeddings()
            if (untaggedWithEmbeddings.size < 2) return

            val merged = mutableSetOf<Long>()
            for (i in untaggedWithEmbeddings.indices) {
                val a = untaggedWithEmbeddings[i]
                if (a.id in merged || a.embeddingSampleCount < 2) continue
                val embA = a.embedding?.let { PeopleRepository.base64ToEmbedding(it) } ?: continue

                for (j in i + 1 until untaggedWithEmbeddings.size) {
                    val b = untaggedWithEmbeddings[j]
                    if (b.id in merged || b.embeddingSampleCount < 2) continue
                    val embB = b.embedding?.let { PeopleRepository.base64ToEmbedding(it) } ?: continue

                    val similarity = PeopleRepository.cosineSimilarity(embA, embB)
                    if (similarity > 0.75f) {
                        val (keep, merge) = if (a.embeddingSampleCount >= b.embeddingSampleCount) a to b else b to a
                        people.mergeProfiles(keep.id, listOf(merge.id))
                        merged.add(merge.id)
                        Log.i(TAG, "Auto-merged untagged profiles ${merge.id} → ${keep.id} (similarity=${"%.2f".format(similarity)})")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-merge check failed: ${e.message}")
        }
    }

    /**
     * Auto-tag untagged profiles by matching their voice embedding against tagged profiles.
     * If similarity > 0.80, auto-tag with that name and merge into the tagged profile.
     */
    private suspend fun autoTagFromVoiceMatch(people: PeopleRepository) {
        try {
            val untagged = people.getUntaggedWithEmbeddings()
            if (untagged.isEmpty()) return

            for (profile in untagged) {
                if (profile.embeddingSampleCount < 2) continue
                val embedding = profile.embedding?.let { PeopleRepository.base64ToEmbedding(it) } ?: continue

                val matches = people.findSpeakerMatches(embedding, topK = 1)
                val best = matches.firstOrNull() ?: continue

                // Only auto-merge if it's a tagged profile with high confidence
                if (best.profile.label != null && best.score > 0.80f && best.profile.id != profile.id) {
                    people.mergeProfiles(best.profile.id, listOf(profile.id))
                    Log.i(TAG, "Auto-tagged+merged profile ${profile.id} → ${best.profile.label} (id=${best.profile.id}, similarity=${"%.2f".format(best.score)})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-tag failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AnalysisRepository"
    }
}
