package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recording_chunks",
    indices = [Index(value = ["date"]), Index(value = ["sessionId"])]
)
data class RecordingChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val filePath: String,
    val fileName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val batteryLevelStart: Int,
    val batteryLevelEnd: Int,
    val interruptedBy: String? = null,
    val date: String,                   // YYYY-MM-DD
    val isProcessed: Boolean = false    // true after AI transcription (Phase 2)
)
