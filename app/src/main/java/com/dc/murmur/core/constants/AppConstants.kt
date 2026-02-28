package com.dc.murmur.core.constants

import android.os.Environment
import java.io.File

object AppConstants {

    // --- Recording ---
    const val CHUNK_DURATION_DEFAULT_MS = 5 * 60 * 1000L  // 5 minutes (testing; production: 10 minutes)
    const val AUDIO_SAMPLE_RATE = 16000          // 16 kHz
    const val AUDIO_BIT_RATE_LOW = 24000         // 24 kbps
    const val AUDIO_BIT_RATE_NORMAL = 32000      // 32 kbps
    const val AUDIO_BIT_RATE_HIGH = 64000        // 64 kbps
    const val AUDIO_CHANNELS = 1                 // Mono
    const val CHUNK_GAP_MAX_MS = 500L            // Max gap between chunks
    const val RECORDING_RETRY_COUNT = 3
    const val RECORDING_RETRY_DELAY_MS = 5000L

    // --- Storage ---
    val BASE_DIR: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "Murmur"
    )
    val RECORDINGS_DIR = File(BASE_DIR, "recordings")
    val LOGS_DIR = File(BASE_DIR, "logs")
    const val LOW_STORAGE_WARNING_MB = 500L
    const val LOW_STORAGE_CRITICAL_MB = 100L
    const val FILE_EXTENSION = ".m4a"
    const val CHUNK_FILE_PREFIX = "chunk_"

    // --- Notification ---
    const val RECORDING_CHANNEL_ID = "murmur_recording"
    const val RECORDING_CHANNEL_NAME = "Recording"
    const val ANALYSIS_CHANNEL_ID = "murmur_analysis"
    const val ANALYSIS_CHANNEL_NAME = "Analysis"
    const val INSIGHTS_CHANNEL_ID = "murmur_insights"
    const val INSIGHTS_CHANNEL_NAME = "Insights"
    const val RECORDING_NOTIFICATION_ID = 1001
    const val ANALYSIS_NOTIFICATION_ID = 1002
    const val INSIGHTS_NOTIFICATION_ID = 1003
    const val PREDICTION_NOTIFICATION_ID = 1004
    const val PREDICTION_CHANNEL_ID = "murmur_predictions"
    const val PREDICTION_CHANNEL_NAME = "Predictions"

    // --- Service ---
    const val ACTION_START_RECORDING = "com.dc.murmur.START_RECORDING"
    const val ACTION_STOP_RECORDING = "com.dc.murmur.STOP_RECORDING"
    const val ACTION_PAUSE_RECORDING = "com.dc.murmur.PAUSE_RECORDING"
    const val ACTION_RESUME_RECORDING = "com.dc.murmur.RESUME_RECORDING"
    const val EXTRA_PAUSE_REASON = "extra_pause_reason"
    const val WAKELOCK_TAG = "Murmur::RecordingWakeLock"

    // --- Call Handling ---
    const val CALL_RESUME_DELAY_MS = 2000L       // Wait 2s after call ends

    // --- Battery ---
    const val BATTERY_LOG_INTERVAL_MS = 30 * 60 * 1000L  // 30 minutes
    const val MIN_BATTERY_FOR_ANALYSIS = 20      // Don't analyze below 20%

    // --- Analysis ---
    const val ANALYSIS_WORKER_TAG = "murmur_analysis_worker"
    const val ANALYSIS_SCHEDULED_WORKER_TAG = "murmur_scheduled_analysis"

    // --- DataStore Keys ---
    const val PREFS_NAME = "murmur_prefs"
    const val PREF_WAS_RECORDING = "was_recording_active"
    const val PREF_LAST_SESSION_ID = "last_session_id"
    const val PREF_CHUNK_DURATION_MS = "chunk_duration_ms"
    const val PREF_AUDIO_QUALITY = "audio_quality"      // "low", "normal", "high"
    const val PREF_AUTO_DELETE_DAYS = "auto_delete_days"
    const val PREF_AUTO_START_ON_BOOT = "auto_start_on_boot"
    const val PREF_ANALYSIS_ENABLED = "analysis_enabled"
    const val PREF_ANALYSIS_MODE = "analysis_mode"      // "fixed_time", "on_charging", "manual"
    const val PREF_ANALYSIS_HOUR = "analysis_hour"
    const val PREF_ANALYSIS_MINUTE = "analysis_minute"
    const val PREF_ANALYSIS_DAYS = "analysis_days"       // Set of day strings
    const val PREF_REQUIRE_CHARGING = "require_charging_for_analysis"
    const val PREF_MIN_BATTERY = "min_battery_for_analysis"
    const val PREF_ACTIVE_SPEECH_MODEL = "active_speech_model"
    const val PREF_CLAUDE_BRIDGE_PORT = "claude_bridge_port"
    const val PREF_CLAUDE_BRIDGE_AUTO_START = "claude_bridge_auto_start"
    const val PREF_TRANSCRIPTION_LANGUAGE = "transcription_language"
    const val DEFAULT_CLAUDE_BRIDGE_PORT = 8735
}
