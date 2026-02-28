package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "predictions",
    indices = [
        Index("date"),
        Index("isActive")
    ]
)
data class PredictionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val predictionType: String,
    val message: String,
    val confidence: Float,
    val basedOnDays: Int,
    val triggerTime: Long? = null,
    val date: String,                // YYYY-MM-DD
    val isActive: Boolean = true,
    val wasFulfilled: Boolean? = null,
    val createdAt: Long
)
