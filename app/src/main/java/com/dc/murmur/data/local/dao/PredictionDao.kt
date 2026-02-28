package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.PredictionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PredictionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prediction: PredictionEntity): Long

    @Query("SELECT * FROM predictions WHERE date = :date AND isActive = 1 ORDER BY confidence DESC")
    fun getActiveForDate(date: String): Flow<List<PredictionEntity>>

    @Query("SELECT * FROM predictions WHERE isActive = 1 ORDER BY createdAt DESC LIMIT :limit")
    fun getActive(limit: Int): Flow<List<PredictionEntity>>

    @Query("UPDATE predictions SET isActive = 0 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE predictions SET wasFulfilled = :fulfilled WHERE id = :id")
    suspend fun markFulfillment(id: Long, fulfilled: Boolean)

    @Query("DELETE FROM predictions WHERE isActive = 0 AND createdAt < :before")
    suspend fun deleteOldDismissed(before: Long)
}
