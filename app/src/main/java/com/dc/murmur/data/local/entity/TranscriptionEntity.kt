package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcriptions",
    foreignKeys = [
        ForeignKey(
            entity = RecordingChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chunkId")]
)
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chunkId: Long,
    val text: String,
    val language: String = "en",
    val sentiment: String,           // "positive" | "negative" | "neutral"
    val sentimentScore: Float,       // 0.0 – 1.0
    val keywords: String,            // JSON array of strings
    val processedAt: Long,
    val modelUsed: String            // e.g. "whisper-tiny"
)
