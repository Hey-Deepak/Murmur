package com.dc.murmur.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dc.murmur.core.util.BatteryUtil
import com.dc.murmur.core.util.NotificationUtil
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.repository.AnalysisRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val pipeline: AnalysisPipeline by inject()
    private val analysisRepo: AnalysisRepository by inject()
    private val chunkDao: RecordingChunkDao by inject()
    private val batteryUtil: BatteryUtil by inject()
    private val notificationUtil: NotificationUtil by inject()
    private val analysisState: AnalysisStateHolder by inject()

    override suspend fun doWork(): Result {
        analysisState.clearLog()
        pipeline.setLogCallback { analysisState.addLog(it) }
        pipeline.setStepCallback { analysisState.setStep(it) }

        // Check battery
        val battery = batteryUtil.getBatteryLevel()
        analysisState.addLog("Battery: $battery%")
        if (battery in 0..14) {
            analysisState.addLog("Battery too low, aborting")
            analysisState.setError("Battery too low ($battery%)")
            notificationUtil.showAnalysisSkipped("Battery too low ($battery%)")
            return Result.failure()
        }

        try {
            val chunks = chunkDao.getUnprocessed()
            analysisState.addLog("Found ${chunks.size} unprocessed chunks")
            if (chunks.isEmpty()) {
                analysisState.addLog("Nothing to process")
                analysisState.setCompleted()
                return Result.success()
            }

            // Initialize models
            analysisState.setDownloading()
            analysisState.addLog("Initializing pipeline...")
            pipeline.initialize { progress ->
                // Update download progress in state (on main flow)
            }
            analysisState.addLog("Pipeline ready")

            val total = chunks.size
            var processed = 0

            for (chunk in chunks) {
                // Check if cancelled
                if (isStopped) {
                    analysisState.addLog("Cancelled by user")
                    analysisState.setIdle()
                    return Result.failure()
                }

                // Check battery during processing
                val currentBattery = batteryUtil.getBatteryLevel()
                if (currentBattery in 0..9) {
                    analysisState.addLog("Battery dropped to $currentBattery%, pausing")
                    analysisState.setError("Battery dropped to $currentBattery%, pausing analysis")
                    notificationUtil.cancelAnalysis()
                    return Result.retry()
                }

                processed++
                analysisState.setRunning(processed, total)
                notificationUtil.showAnalysisProgress(processed, total)

                try {
                    val result = pipeline.processChunk(chunk.id, chunk.filePath)
                    analysisRepo.saveTranscription(result)
                } catch (e: Exception) {
                    analysisState.addLog("ERROR on chunk ${chunk.id}: ${e.message}")
                    // Skip this chunk but continue with others
                    // Mark as processed to avoid infinite retry loops
                    chunkDao.markProcessed(chunk.id)
                }
            }

            pipeline.close()
            notificationUtil.cancelAnalysis()
            notificationUtil.showInsightsReady()
            analysisState.addLog("All $total chunks processed")
            analysisState.setCompleted()

            return Result.success()
        } catch (e: Exception) {
            analysisState.addLog("FATAL: ${e.message}")
            pipeline.close()
            notificationUtil.cancelAnalysis()
            analysisState.setError(e.message ?: "Analysis failed")
            return Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "murmur_analysis_now"

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnalysisWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
