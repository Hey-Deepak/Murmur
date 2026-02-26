package com.dc.murmur.data.repository

import com.dc.murmur.ai.AnalysisResult
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.local.dao.TranscriptionDao
import com.dc.murmur.data.local.entity.TranscriptionEntity
import kotlinx.coroutines.flow.Flow

class AnalysisRepository(
    private val transcriptionDao: TranscriptionDao,
    private val chunkDao: RecordingChunkDao
) {

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

    suspend fun clearAllTranscriptions() {
        transcriptionDao.deleteAll()
    }
}
