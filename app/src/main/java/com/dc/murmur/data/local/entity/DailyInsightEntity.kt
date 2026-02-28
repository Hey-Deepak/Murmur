package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_insights",
    indices = [Index(value = ["date"], unique = true)]
)
data class DailyInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                    // YYYY-MM-DD
    val timelineJson: String,
    val timeBreakdownJson: String,
    val peopleSummaryJson: String,
    val topTopics: String,
    val highlight: String? = null,
    val overallSentiment: String,
    val overallSentimentScore: Float,
    val totalRecordedMs: Long,
    val totalAnalyzedChunks: Int,
    val generatedAt: Long
)
