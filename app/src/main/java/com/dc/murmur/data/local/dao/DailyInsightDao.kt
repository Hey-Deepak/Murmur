package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.DailyInsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyInsightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(insight: DailyInsightEntity): Long

    @Query("SELECT * FROM daily_insights WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyInsightEntity?

    @Query("SELECT * FROM daily_insights WHERE date = :date LIMIT 1")
    fun getByDateFlow(date: String): Flow<DailyInsightEntity?>

    @Query("SELECT * FROM daily_insights ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<DailyInsightEntity>>

    @Query("SELECT * FROM daily_insights WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getRange(startDate: String, endDate: String): List<DailyInsightEntity>

    @Query("DELETE FROM daily_insights WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
