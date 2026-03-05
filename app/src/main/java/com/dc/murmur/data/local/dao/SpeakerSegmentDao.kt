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

data class SpeakerSegmentWithProfile(
    val id: Long,
    val chunkId: Long,
    val voiceProfileId: Long?,
    val speakerLabel: String,
    val speakingDurationMs: Long,
    val turnCount: Int,
    val role: String?,
    val emotionalState: String?,
    val segmentTimings: String?,
    val profileLabel: String?,
    val profileEmbedding: String?
)

data class CoSpeakerInfo(
    val profileId: Long,
    val label: String?,
    val sharedChunks: Int
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

    @Query("UPDATE speaker_segments SET voiceProfileId = :targetProfileId WHERE voiceProfileId = :sourceProfileId")
    suspend fun reassignSegments(sourceProfileId: Long, targetProfileId: Long)

    @Query("SELECT COALESCE(SUM(speakingDurationMs), 0) FROM speaker_segments WHERE voiceProfileId = :profileId")
    suspend fun getTotalSpeakingTime(profileId: Long): Long

    @Query("""
        SELECT s.id, s.chunkId, s.voiceProfileId, s.speakerLabel, s.speakingDurationMs,
               s.turnCount, s.role, s.emotionalState, s.segmentTimings,
               p.label AS profileLabel, p.embedding AS profileEmbedding
        FROM speaker_segments s
        LEFT JOIN voice_profiles p ON s.voiceProfileId = p.id
        WHERE s.chunkId = :chunkId
    """)
    suspend fun getByChunkWithProfile(chunkId: Long): List<SpeakerSegmentWithProfile>

    @Query("""
        SELECT s2.voiceProfileId AS profileId, vp.label, COUNT(*) AS sharedChunks
        FROM speaker_segments s1
        JOIN speaker_segments s2 ON s1.chunkId = s2.chunkId AND s1.voiceProfileId != s2.voiceProfileId
        JOIN voice_profiles vp ON s2.voiceProfileId = vp.id
        WHERE s1.voiceProfileId = :profileId AND s2.voiceProfileId IS NOT NULL
        GROUP BY s2.voiceProfileId
        ORDER BY sharedChunks DESC
        LIMIT 5
    """)
    suspend fun getCoSpeakers(profileId: Long): List<CoSpeakerInfo>
}
