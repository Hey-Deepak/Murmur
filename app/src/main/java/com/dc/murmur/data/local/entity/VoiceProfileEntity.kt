package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_profiles",
    indices = [Index("label")]
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
    val notes: String? = null
)
