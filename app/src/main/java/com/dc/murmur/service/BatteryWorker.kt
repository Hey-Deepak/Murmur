package com.dc.murmur.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dc.murmur.core.util.BatteryUtil
import com.dc.murmur.data.local.entity.BatteryLogEntity
import com.dc.murmur.data.repository.BatteryRepository
import com.dc.murmur.data.repository.RecordingRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class BatteryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val batteryRepo: BatteryRepository by inject()
    private val recordingRepo: RecordingRepository by inject()
    private val batteryUtil: BatteryUtil by inject()

    override suspend fun doWork(): Result {
        val sessionId = recordingRepo.currentSessionId.value

        batteryRepo.logEvent(
            BatteryLogEntity(
                timestamp = System.currentTimeMillis(),
                batteryLevel = batteryUtil.getBatteryLevel(),
                isCharging = batteryUtil.isCharging(),
                temperature = batteryUtil.getTemperatureCelsius(),
                sessionId = sessionId,
                event = "periodic"
            )
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "murmur_battery_logger"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BatteryWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
