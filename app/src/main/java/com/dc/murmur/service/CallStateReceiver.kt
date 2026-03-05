package com.dc.murmur.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.dc.murmur.core.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dc.murmur.core.constants.AppConstants

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Pause recording for incoming/outgoing call
                sendToService(
                    context,
                    Intent(context, RecordingService::class.java).apply {
                        action = AppConstants.ACTION_PAUSE_RECORDING
                        putExtra(AppConstants.EXTRA_PAUSE_REASON, "phone_call")
                    }
                )
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Wait 2s after call ends then resume
                CoroutineScope(Dispatchers.IO).launch {
                    delay(AppConstants.CALL_RESUME_DELAY_MS)
                    sendToService(
                        context,
                        Intent(context, RecordingService::class.java).apply {
                            action = AppConstants.ACTION_RESUME_RECORDING
                        }
                    )
                }
            }
        }
    }

    private fun sendToService(context: Context, intent: Intent) {
        try {
            // Use startService, not startForegroundService. The recording service
            // is already running in the foreground — this just delivers the intent.
            // If the service isn't running, PAUSE/RESUME are no-ops anyway.
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not deliver ${intent.action} to RecordingService", e)
            CrashLogger.logException(e, "CallStateReceiver")
        }
    }
}
