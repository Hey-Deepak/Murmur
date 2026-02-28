package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = RecordingChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("chunkId"),
        Index("date"),
        Index("activityType")
    ]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chunkId: Long,
    val activityType: String,        // eating|meeting|working|commuting|idle|phone_call|casual_chat|solo
    val confidence: Float,
    val subActivity: String? = null,
    val date: String,                // YYYY-MM-DD
    val startTime: Long,
    val durationMs: Long,
    val detectedAt: Long
)
