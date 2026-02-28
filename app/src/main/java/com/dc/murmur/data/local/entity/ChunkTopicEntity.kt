package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chunk_topics",
    primaryKeys = ["chunkId", "topicId"],
    foreignKeys = [
        ForeignKey(
            entity = RecordingChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("chunkId"),
        Index("topicId")
    ]
)
data class ChunkTopicEntity(
    val chunkId: Long,
    val topicId: Long,
    val relevance: Float = 1.0f,
    val keyPoints: String? = null
)
