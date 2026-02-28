package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.TranscriptionEntity
import com.dc.murmur.data.local.entity.TranscriptionWithChunk
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcription: TranscriptionEntity): Long

    @Query("SELECT * FROM transcriptions WHERE chunkId = :chunkId LIMIT 1")
    suspend fun getByChunk(chunkId: Long): TranscriptionEntity?

    @Query("""
        SELECT t.* FROM transcriptions t
        INNER JOIN recording_chunks c ON t.chunkId = c.id
        WHERE c.date = :date
        ORDER BY c.startTime ASC
    """)
    fun getByDate(date: String): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions ORDER BY processedAt DESC")
    fun getAll(): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions ORDER BY processedAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<TranscriptionEntity>>

    @Query("DELETE FROM transcriptions WHERE chunkId IN (SELECT id FROM recording_chunks WHERE date < :beforeDate)")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()

    @Query("""
        SELECT t.id, t.chunkId, t.text, t.language, t.sentiment, t.sentimentScore,
               t.keywords, t.processedAt, t.modelUsed,
               t.activityType, t.speakerCount, t.topicsSummary, t.behavioralTags,
               t.keyMoment, t.analysisVersion,
               c.fileName, c.startTime, c.durationMs, c.date
        FROM transcriptions t
        INNER JOIN recording_chunks c ON t.chunkId = c.id
        ORDER BY c.startTime DESC
    """)
    fun getAllWithChunks(): Flow<List<TranscriptionWithChunk>>

    @Query("""
        SELECT t.id, t.chunkId, t.text, t.language, t.sentiment, t.sentimentScore,
               t.keywords, t.processedAt, t.modelUsed,
               t.activityType, t.speakerCount, t.topicsSummary, t.behavioralTags,
               t.keyMoment, t.analysisVersion,
               c.fileName, c.startTime, c.durationMs, c.date
        FROM transcriptions t
        INNER JOIN recording_chunks c ON t.chunkId = c.id
        WHERE t.text LIKE '%' || :query || '%'
           OR c.fileName LIKE '%' || :query || '%'
           OR c.date LIKE '%' || :query || '%'
        ORDER BY c.startTime DESC
    """)
    fun searchWithChunks(query: String): Flow<List<TranscriptionWithChunk>>

    @Query("""
        SELECT t.id, t.chunkId, t.text, t.language, t.sentiment, t.sentimentScore,
               t.keywords, t.processedAt, t.modelUsed,
               t.activityType, t.speakerCount, t.topicsSummary, t.behavioralTags,
               t.keyMoment, t.analysisVersion,
               c.fileName, c.startTime, c.durationMs, c.date
        FROM transcriptions t
        INNER JOIN recording_chunks c ON t.chunkId = c.id
        WHERE t.chunkId = :chunkId
        LIMIT 1
    """)
    suspend fun getWithChunkByChunkId(chunkId: Long): TranscriptionWithChunk?
}
