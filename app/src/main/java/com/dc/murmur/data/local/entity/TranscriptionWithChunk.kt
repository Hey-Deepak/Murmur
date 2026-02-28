package com.dc.murmur.data.local.entity

data class TranscriptionWithChunk(
    // From TranscriptionEntity
    val id: Long,
    val chunkId: Long,
    val text: String,
    val language: String,
    val sentiment: String,
    val sentimentScore: Float,
    val keywords: String,
    val processedAt: Long,
    val modelUsed: String,
    val activityType: String? = null,
    val speakerCount: Int? = null,
    val topicsSummary: String? = null,
    val behavioralTags: String? = null,
    val keyMoment: String? = null,
    val analysisVersion: Int = 1,
    // From RecordingChunkEntity
    val fileName: String,
    val startTime: Long,
    val durationMs: Long,
    val date: String
)
