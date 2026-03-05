package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: RecordingChunkEntity): Long

    @Query("SELECT * FROM recording_chunks WHERE date = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<RecordingChunkEntity>>

    @Query("SELECT * FROM recording_chunks WHERE date = :date ORDER BY startTime ASC")
    suspend fun getByDateOnce(date: String): List<RecordingChunkEntity>

    @Query("SELECT * FROM recording_chunks ORDER BY startTime DESC")
    fun getAll(): Flow<List<RecordingChunkEntity>>

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM recording_chunks")
    fun getTotalSize(): Flow<Long>

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM recording_chunks")
    suspend fun getTotalSizeOnce(): Long

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM recording_chunks WHERE date = :date")
    fun getTodayDuration(date: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM recording_chunks WHERE date = :date")
    fun getTodaySize(date: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM recording_chunks WHERE date = :date")
    fun getTodayCount(date: String): Flow<Int>

    @Query("SELECT * FROM recording_chunks WHERE isProcessed = 0 ORDER BY startTime ASC")
    suspend fun getUnprocessed(): List<RecordingChunkEntity>

    @Query("SELECT COUNT(*) FROM recording_chunks WHERE isProcessed = 0")
    suspend fun getUnprocessedCount(): Int

    @Query("SELECT COUNT(*) FROM recording_chunks WHERE isProcessed = 0")
    fun getUnprocessedCountFlow(): Flow<Int>

    @Query("UPDATE recording_chunks SET isProcessed = 1 WHERE id = :id")
    suspend fun markProcessed(id: Long)

    @Query("SELECT * FROM recording_chunks WHERE fileName LIKE '%' || :query || '%' OR date LIKE '%' || :query || '%' ORDER BY startTime DESC")
    fun search(query: String): Flow<List<RecordingChunkEntity>>

    @Query("DELETE FROM recording_chunks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT filePath FROM recording_chunks")
    suspend fun getAllFilePaths(): List<String>

    @Query("SELECT * FROM recording_chunks WHERE id = :id")
    suspend fun getById(id: Long): RecordingChunkEntity?

    @Query("DELETE FROM recording_chunks")
    suspend fun deleteAll()

    @Query("DELETE FROM recording_chunks WHERE date < :date")
    suspend fun deleteOlderThan(date: String)

    @Query("UPDATE recording_chunks SET isProcessed = 0 WHERE isProcessed = 1")
    suspend fun resetAllProcessed(): Int

    @Query("SELECT DISTINCT date FROM recording_chunks ORDER BY date DESC")
    fun getAllDates(): Flow<List<String>>
}
