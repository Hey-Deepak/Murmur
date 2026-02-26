package com.dc.murmur

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dc.murmur.core.constants.AppConstants
import com.dc.murmur.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MurmurApplication : Application() {

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
