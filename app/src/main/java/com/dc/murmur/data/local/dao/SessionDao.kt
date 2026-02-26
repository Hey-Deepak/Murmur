package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dc.murmur.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1")
    fun getActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveOnce(): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?
}
