package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

data class ActivityTimeBreakdown(
    val activityType: String,
    val totalMs: Long
)

@Dao
interface ActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ActivityEntity): Long

    @Query("SELECT * FROM activities WHERE chunkId = :chunkId")
    suspend fun getByChunk(chunkId: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE date = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<ActivityEntity>>

    @Query("SELECT activityType, SUM(durationMs) as totalMs FROM activities WHERE date = :date GROUP BY activityType ORDER BY totalMs DESC")
    suspend fun getTimeBreakdown(date: String): List<ActivityTimeBreakdown>

    @Query("SELECT activityType, SUM(durationMs) as totalMs FROM activities WHERE date >= :startDate AND date <= :endDate GROUP BY activityType ORDER BY totalMs DESC")
    suspend fun getTimeBreakdownRange(startDate: String, endDate: String): List<ActivityTimeBreakdown>

    @Query("SELECT DISTINCT date FROM activities ORDER BY date DESC")
    fun getAllDates(): Flow<List<String>>

    @Query("DELETE FROM activities WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
