package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.BatteryLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BatteryLogEntity)

    @Query("SELECT * FROM battery_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: String): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :startOfDay ORDER BY timestamp ASC")
    fun getToday(startOfDay: Long): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :startOfDay ORDER BY timestamp ASC")
    suspend fun getTodayOnce(startOfDay: Long): List<BatteryLogEntity>

    /**
     * Average battery drain per hour computed from chunk_start/chunk_end pairs
     * over the given time window (since epoch ms).
     */
    @Query("""
        SELECT COALESCE(AVG(drain_per_hour), 0.0) FROM (
            SELECT
                CAST((MAX(batteryLevel) - MIN(batteryLevel)) AS REAL)
                / CAST((MAX(timestamp) - MIN(timestamp)) AS REAL) * 3600000.0 AS drain_per_hour
            FROM battery_logs
            WHERE timestamp >= :since AND event IN ('chunk_start', 'chunk_end')
            GROUP BY sessionId
            HAVING COUNT(*) >= 2
        )
    """)
    suspend fun getAvgDrainPerHour(since: Long): Float

    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<BatteryLogEntity>>

    @Query("DELETE FROM battery_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
