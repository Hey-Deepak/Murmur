package com.dc.murmur.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_logs")
data class BatteryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val batteryLevel: Int,              // 0–100
    val isCharging: Boolean,
    val temperature: Float,             // Celsius
    val sessionId: String? = null,
    val event: String                   // chunk_start | chunk_end | periodic | charging_changed
)
