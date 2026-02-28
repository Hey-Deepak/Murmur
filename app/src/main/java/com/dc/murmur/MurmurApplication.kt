package com.dc.murmur

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.dc.murmur.ai.AnalysisWorker
import com.dc.murmur.core.constants.AppConstants
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.repository.SettingsRepository
import com.dc.murmur.di.appModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MurmurApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Start Koin DI
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MurmurApplication)
            modules(appModules)
        }

        // Create notification channels
        createNotificationChannels()

        // Auto-trigger analysis for any unprocessed chunks
        autoTriggerAnalysis()
    }

    private fun autoTriggerAnalysis() {
        val chunkDao: RecordingChunkDao by inject()
        val settingsRepo: SettingsRepository by inject()
        appScope.launch {
            try {
                val enabled = settingsRepo.getAnalysisEnabled()
                if (!enabled) {
                    Log.w("MURMUR", "Auto-analysis: disabled in settings, skipping")
                    return@launch
                }
                val unprocessed = chunkDao.getUnprocessed()
                if (unprocessed.isNotEmpty()) {
                    Log.w("MURMUR", "Auto-analysis: found ${unprocessed.size} unprocessed chunks, enqueuing worker")
                    AnalysisWorker.enqueueNow(this@MurmurApplication)
                } else {
                    Log.w("MURMUR", "Auto-analysis: no unprocessed chunks")
                }
            } catch (e: Exception) {
                Log.e("MURMUR", "Auto-analysis check failed", e)
            }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Recording channel — LOW importance (silent persistent notification)
        val recordingChannel = NotificationChannel(
            AppConstants.RECORDING_CHANNEL_ID,
            AppConstants.RECORDING_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Murmur is actively recording"
            setShowBadge(false)
        }

        // Analysis channel — DEFAULT importance
        val analysisChannel = NotificationChannel(
            AppConstants.ANALYSIS_CHANNEL_ID,
            AppConstants.ANALYSIS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows analysis progress"
            setShowBadge(false)
        }

        // Insights channel — HIGH importance (daily summary ready)
        val insightsChannel = NotificationChannel(
            AppConstants.INSIGHTS_CHANNEL_ID,
            AppConstants.INSIGHTS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies when daily insights are ready"
        }

        manager.createNotificationChannels(
            listOf(recordingChannel, analysisChannel, insightsChannel)
        )
    }
}
