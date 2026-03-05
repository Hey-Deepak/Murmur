package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dc.murmur.data.local.entity.VoiceProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VoiceProfileEntity): Long

    @Update
    suspend fun update(profile: VoiceProfileEntity)

    @Query("SELECT * FROM voice_profiles ORDER BY lastSeenAt DESC")
    fun getAll(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles WHERE id = :id")
    suspend fun getById(id: Long): VoiceProfileEntity?

    @Query("SELECT * FROM voice_profiles WHERE voiceId = :voiceId LIMIT 1")
    suspend fun getByVoiceId(voiceId: String): VoiceProfileEntity?

    @Query("SELECT * FROM voice_profiles WHERE label IS NOT NULL ORDER BY lastSeenAt DESC")
    fun getTagged(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles WHERE label IS NULL ORDER BY lastSeenAt DESC")
    fun getUntagged(): Flow<List<VoiceProfileEntity>>

    @Query("UPDATE voice_profiles SET label = :label WHERE id = :id")
    suspend fun setLabel(id: Long, label: String)

    @Query("UPDATE voice_profiles SET photoUri = :photoUri WHERE id = :id")
    suspend fun setPhoto(id: Long, photoUri: String)

    @Query("UPDATE voice_profiles SET totalInteractionMs = totalInteractionMs + :addMs, interactionCount = interactionCount + 1, lastSeenAt = :lastSeen WHERE id = :id")
    suspend fun incrementInteraction(id: Long, addMs: Long, lastSeen: Long)

    @Query("UPDATE voice_profiles SET embedding = :embedding WHERE id = :id")
    suspend fun setEmbedding(id: Long, embedding: String)

    @Query("UPDATE voice_profiles SET embedding = :embedding, embeddingSampleCount = :sampleCount, embeddingUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEmbedding(id: Long, embedding: String, sampleCount: Int, updatedAt: Long)

    @Query("SELECT * FROM voice_profiles WHERE embedding IS NOT NULL")
    suspend fun getWithEmbeddings(): List<VoiceProfileEntity>

    @Query("SELECT * FROM voice_profiles WHERE embedding IS NOT NULL AND label IS NULL")
    suspend fun getUntaggedWithEmbeddings(): List<VoiceProfileEntity>

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
