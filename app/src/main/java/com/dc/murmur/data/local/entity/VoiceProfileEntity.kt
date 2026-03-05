package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_profiles",
    indices = [Index("label"), Index("voiceId")]
)
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voiceId: String,
    val label: String? = null,
    val photoUri: String? = null,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val totalInteractionMs: Long = 0,
    val interactionCount: Int = 0,
    val notes: String? = null,
    val embedding: String? = null, // base64-encoded FloatArray (speaker embedding)
    val embeddingSampleCount: Int = 0,
    val embeddingUpdatedAt: Long = 0
)
