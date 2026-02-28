package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "speaker_segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VoiceProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["voiceProfileId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("chunkId"),
        Index("voiceProfileId")
    ]
)
data class SpeakerSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chunkId: Long,
    val voiceProfileId: Long? = null,
    val speakerLabel: String,
    val speakingDurationMs: Long,
    val turnCount: Int,
    val role: String? = null,
    val emotionalState: String? = null
)
