package com.dc.murmur.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dc.murmur.ai.SpeechModelCatalog
import com.dc.murmur.core.constants.AppConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = AppConstants.PREFS_NAME)

class SettingsRepository(private val context: Context) {

    private val ds = context.dataStore

    // --- Keys ---
    private val keyWasRecording      = booleanPreferencesKey(AppConstants.PREF_WAS_RECORDING)
    private val keyLastSessionId     = stringPreferencesKey(AppConstants.PREF_LAST_SESSION_ID)
    private val keyChunkDuration     = longPreferencesKey(AppConstants.PREF_CHUNK_DURATION_MS)
    private val keyAudioQuality      = stringPreferencesKey(AppConstants.PREF_AUDIO_QUALITY)
    private val keyAutoDeleteDays    = intPreferencesKey(AppConstants.PREF_AUTO_DELETE_DAYS)
    private val keyAutoStartOnBoot   = booleanPreferencesKey(AppConstants.PREF_AUTO_START_ON_BOOT)
    private val keyAnalysisEnabled   = booleanPreferencesKey(AppConstants.PREF_ANALYSIS_ENABLED)
    private val keyAnalysisMode      = stringPreferencesKey(AppConstants.PREF_ANALYSIS_MODE)
    private val keyAnalysisHour      = intPreferencesKey(AppConstants.PREF_ANALYSIS_HOUR)
    private val keyAnalysisMinute    = intPreferencesKey(AppConstants.PREF_ANALYSIS_MINUTE)
    private val keyAnalysisDays      = stringSetPreferencesKey(AppConstants.PREF_ANALYSIS_DAYS)
    private val keyRequireCharging   = booleanPreferencesKey(AppConstants.PREF_REQUIRE_CHARGING)
    private val keyMinBattery        = intPreferencesKey(AppConstants.PREF_MIN_BATTERY)
    private val keyActiveSpeechModel = stringPreferencesKey(AppConstants.PREF_ACTIVE_SPEECH_MODEL)

    // --- Recording state ---
    val wasRecording: Flow<Boolean> = ds.data.map { it[keyWasRecording] ?: false }
    suspend fun setWasRecording(value: Boolean) { ds.edit { it[keyWasRecording] = value } }

    val lastSessionId: Flow<String?> = ds.data.map { it[keyLastSessionId] }
    suspend fun setLastSessionId(id: String) { ds.edit { it[keyLastSessionId] = id } }

    // --- Recording config ---
    val chunkDurationMs: Flow<Long> = ds.data.map { it[keyChunkDuration] ?: AppConstants.CHUNK_DURATION_DEFAULT_MS }
    suspend fun getChunkDurationMs(): Long = chunkDurationMs.first()
    suspend fun setChunkDurationMs(ms: Long) { ds.edit { it[keyChunkDuration] = ms } }

    val audioQuality: Flow<String> = ds.data.map { it[keyAudioQuality] ?: "normal" }
    suspend fun getAudioQuality(): String = audioQuality.first()
    suspend fun setAudioQuality(quality: String) { ds.edit { it[keyAudioQuality] = quality } }

    fun getBitRate(quality: String): Int = when (quality) {
        "low"  -> AppConstants.AUDIO_BIT_RATE_LOW
        "high" -> AppConstants.AUDIO_BIT_RATE_HIGH
        else   -> AppConstants.AUDIO_BIT_RATE_NORMAL
    }

    val autoDeleteDays: Flow<Int> = ds.data.map { it[keyAutoDeleteDays] ?: 14 }
    suspend fun setAutoDeleteDays(days: Int) { ds.edit { it[keyAutoDeleteDays] = days } }

    val autoStartOnBoot: Flow<Boolean> = ds.data.map { it[keyAutoStartOnBoot] ?: false }
    suspend fun setAutoStartOnBoot(enabled: Boolean) { ds.edit { it[keyAutoStartOnBoot] = enabled } }

    // --- Analysis config ---
    val analysisEnabled: Flow<Boolean> = ds.data.map { it[keyAnalysisEnabled] ?: true }
    suspend fun setAnalysisEnabled(enabled: Boolean) { ds.edit { it[keyAnalysisEnabled] = enabled } }

    val analysisMode: Flow<String> = ds.data.map { it[keyAnalysisMode] ?: "fixed_time" }
    suspend fun setAnalysisMode(mode: String) { ds.edit { it[keyAnalysisMode] = mode } }

    val analysisHour: Flow<Int> = ds.data.map { it[keyAnalysisHour] ?: 22 }
    val analysisMinute: Flow<Int> = ds.data.map { it[keyAnalysisMinute] ?: 0 }
    suspend fun setAnalysisTime(hour: Int, minute: Int) {
        ds.edit { it[keyAnalysisHour] = hour; it[keyAnalysisMinute] = minute }
    }

    val analysisDays: Flow<Set<String>> = ds.data.map {
        it[keyAnalysisDays] ?: setOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    }
    suspend fun setAnalysisDays(days: Set<String>) { ds.edit { it[keyAnalysisDays] = days } }

    val requireCharging: Flow<Boolean> = ds.data.map { it[keyRequireCharging] ?: true }
    suspend fun setRequireCharging(required: Boolean) { ds.edit { it[keyRequireCharging] = required } }

    val minBattery: Flow<Int> = ds.data.map { it[keyMinBattery] ?: AppConstants.MIN_BATTERY_FOR_ANALYSIS }
    suspend fun setMinBattery(level: Int) { ds.edit { it[keyMinBattery] = level } }

    suspend fun getRequireCharging(): Boolean = requireCharging.first()
    suspend fun getMinBattery(): Int = minBattery.first()
    suspend fun getAnalysisEnabled(): Boolean = analysisEnabled.first()
    suspend fun getAutoStartOnBoot(): Boolean = autoStartOnBoot.first()
    suspend fun getWasRecording(): Boolean = wasRecording.first()

    // --- Speech model selection ---
    val activeSpeechModel: Flow<String> = ds.data.map {
        it[keyActiveSpeechModel] ?: SpeechModelCatalog.defaultModelId
    }
    suspend fun getActiveSpeechModel(): String = activeSpeechModel.first()
    suspend fun setActiveSpeechModel(modelId: String) { ds.edit { it[keyActiveSpeechModel] = modelId } }
}
