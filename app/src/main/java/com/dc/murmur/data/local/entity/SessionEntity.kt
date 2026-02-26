package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,         // UUID
    val startTime: Long,
    val endTime: Long? = null,          // null while session is active
    val totalChunks: Int = 0,
    val totalSizeBytes: Long = 0L,
    val totalDurationMs: Long = 0L,
    val batteryConsumed: Int = 0        // batteryLevelStart - batteryLevelEnd
)
