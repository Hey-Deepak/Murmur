package com.dc.murmur.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dc.murmur.core.constants.AppConstants

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Pause recording for incoming/outgoing call
                context.startService(
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
                    context.startService(
                        Intent(context, RecordingService::class.java).apply {
                            action = AppConstants.ACTION_RESUME_RECORDING
                        }
                    )
                }
            }
        }
    }
}
