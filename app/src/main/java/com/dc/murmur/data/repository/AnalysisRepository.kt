package com.dc.murmur.data.repository

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
            val segments = result.speakers.map { speaker ->
                val profile = people.getOrCreateProfile(
                    voiceId = "chunk_${result.chunkId}_${speaker.label}",
                    now = now
                )
                people.incrementInteraction(
                    profileId = profile.id,
                    addMs = (speaker.speakingRatio * chunkDurationMs).toLong(),
                    lastSeen = now
                )
                SpeakerSegmentEntity(
                    chunkId = result.chunkId,
                    voiceProfileId = profile.id,
                    speakerLabel = speaker.label,
                    speakingDurationMs = (speaker.speakingRatio * chunkDurationMs).toLong(),
                    turnCount = speaker.turnCount,
                    role = speaker.role,
                    emotionalState = speaker.emotionalState
                )
            }
            people.saveSpeakerSegments(segments)
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
}
