package com.dc.murmur.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dc.murmur.data.local.entity.SpeakerSegmentEntity
import kotlinx.coroutines.flow.Flow

data class SpeakerSegmentWithDate(
    val id: Long,
    val chunkId: Long,
    val voiceProfileId: Long?,
    val speakerLabel: String,
    val speakingDurationMs: Long,
    val turnCount: Int,
    val role: String?,
    val emotionalState: String?,
    val date: String,
    val startTime: Long
)

@Dao
interface SpeakerSegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<SpeakerSegmentEntity>)

    @Query("SELECT * FROM speaker_segments WHERE chunkId = :chunkId")
    suspend fun getByChunk(chunkId: Long): List<SpeakerSegmentEntity>

    @Query("SELECT * FROM speaker_segments WHERE voiceProfileId = :profileId ORDER BY chunkId DESC")
    fun getByProfile(profileId: Long): Flow<List<SpeakerSegmentEntity>>

    @Query("""
        SELECT s.id, s.chunkId, s.voiceProfileId, s.speakerLabel, s.speakingDurationMs,
               s.turnCount, s.role, s.emotionalState, c.date, c.startTime
        FROM speaker_segments s
        INNER JOIN recording_chunks c ON s.chunkId = c.id
        WHERE s.voiceProfileId = :profileId
        ORDER BY c.startTime DESC
        LIMIT :limit
    """)
    suspend fun getRecentByProfile(profileId: Long, limit: Int): List<SpeakerSegmentWithDate>

    @Query("UPDATE speaker_segments SET voiceProfileId = :profileId WHERE speakerLabel = :label AND voiceProfileId IS NULL")
    suspend fun tagSpeaker(label: String, profileId: Long)

    @Query("SELECT COALESCE(SUM(speakingDurationMs), 0) FROM speaker_segments WHERE voiceProfileId = :profileId")
    suspend fun getTotalSpeakingTime(profileId: Long): Long
}
