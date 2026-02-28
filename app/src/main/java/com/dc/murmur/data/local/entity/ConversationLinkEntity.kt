package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_links",
    foreignKeys = [
        ForeignKey(
            entity = RecordingChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceChunkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecordingChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetChunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sourceChunkId"),
        Index("targetChunkId"),
        Index("linkType")
    ]
)
data class ConversationLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceChunkId: Long,
    val targetChunkId: Long,
    val linkType: String,            // same_person|same_topic|same_time_slot|cause_effect|continuation
    val description: String? = null,
    val strength: Float = 1.0f,
    val createdAt: Long
)
