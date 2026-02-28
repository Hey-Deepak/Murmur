package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "topics",
    indices = [Index("name")]
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val firstMentionedAt: Long,
    val lastMentionedAt: Long,
    val totalMentions: Int = 1,
    val totalDurationMs: Long = 0,
    val category: String? = null
)
