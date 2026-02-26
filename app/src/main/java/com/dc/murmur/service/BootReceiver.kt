package com.dc.murmur.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dc.murmur.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val settingsRepo: SettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val autoStart = settingsRepo.getAutoStartOnBoot()
            val wasRecording = settingsRepo.getWasRecording()

            if (autoStart || wasRecording) {
                context.startForegroundService(
                    Intent(context, RecordingService::class.java).apply {
                        action = com.dc.murmur.core.constants.AppConstants.ACTION_START_RECORDING
                    }
                )
            }
        }
    }
}
