package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.TranscriptionEntity
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

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()
}
