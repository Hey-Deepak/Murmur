package com.dc.murmur.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dc.murmur.core.util.BatteryUtil
import com.dc.murmur.core.util.NotificationUtil
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private val insightsRepo: InsightsRepository by inject()
    private val peopleRepo: PeopleRepository by inject()
    private val conversationLinker: ConversationLinker by inject()
    private val insightGenerator: InsightGenerator by inject()
    private val predictionEngine: PredictionEngine by inject()
    private val settingsRepo: SettingsRepository by inject()
    private val diarizationModelManager: DiarizationModelManager by inject()
    private val speakerDiarizer: SpeakerDiarizer by inject()

    override suspend fun doWork(): Result {
        Log.w(TAG, "AnalysisWorker started")
        analysisState.clearLog()
        pipeline.setLogCallback { analysisState.addLog(it) }
        pipeline.setStepCallback { analysisState.setStep(it) }

        // Wire up repositories for full analysis
        analysisRepo.setInsightsRepository(insightsRepo)
        analysisRepo.setPeopleRepository(peopleRepo)

        // Check battery
        val battery = batteryUtil.getBatteryLevel()
        Log.w(TAG, "Battery: $battery%")
        analysisState.addLog("Battery: $battery%")
        if (battery in 0..14) {
            analysisState.addLog("Battery too low, aborting")
            analysisState.setError("Battery too low ($battery%)")
            notificationUtil.showAnalysisSkipped("Battery too low ($battery%)")
            return Result.failure()
        }

        try {
            val chunks = chunkDao.getUnprocessed()
            Log.w(TAG, "Found ${chunks.size} unprocessed chunks")
            analysisState.addLog("Found ${chunks.size} unprocessed chunks")
            if (chunks.isEmpty()) {
                analysisState.addLog("Nothing to process")
                analysisState.setCompleted()
                return Result.success()
            }

            // Initialize diarization models (download if needed)
            analysisState.setDownloading()
            if (!diarizationModelManager.areModelsReady()) {
                analysisState.addLog("Downloading speaker diarization models...")
                try {
                    diarizationModelManager.downloadModels { model, progress ->
                        analysisState.addLog("Downloading $model: ${(progress * 100).toInt()}%")
                    }
                    analysisState.addLog("Diarization models downloaded")
                } catch (e: Exception) {
                    Log.w(TAG, "Diarization model download failed: ${e.message}")
                    analysisState.addLog("Diarization models unavailable: ${e.message}")
                }
            }

            // Initialize diarizer (will skip if models not ready)
            try {
                speakerDiarizer.initialize()
                if (speakerDiarizer.isInitialized) {
                    analysisState.addLog("Speaker diarizer ready")
                } else {
                    analysisState.addLog("Speaker diarizer unavailable (models not downloaded)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Speaker diarizer init failed: ${e.message}")
                analysisState.addLog("Speaker diarizer failed: ${e.message}")
            }

            // Initialize pipeline (STT models)
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
                    withContext(NonCancellable) { pipeline.close() }
                    return Result.failure()
                }

                // Check battery during processing
                val currentBattery = batteryUtil.getBatteryLevel()
                if (currentBattery in 0..9) {
                    analysisState.addLog("Battery dropped to $currentBattery%, pausing")
                    analysisState.setError("Battery dropped to $currentBattery%, pausing analysis")
                    withContext(NonCancellable) { pipeline.close() }
                    notificationUtil.cancelAnalysis()
                    return Result.retry()
                }

                processed++
                analysisState.setRunning(processed, total)
                notificationUtil.showAnalysisProgress(processed, total)

                try {
                    // NonCancellable ensures native operations (WhisperKit, sherpa-onnx)
                    // complete before the coroutine can be cancelled. Native code cannot
                    // be safely interrupted — freeing resources mid-call causes SIGSEGV.
                    withContext(NonCancellable) {
                        Log.w(TAG, "Processing chunk ${chunk.id}: ${chunk.filePath}")
                        val result = pipeline.processChunk(chunk.id, chunk.filePath)
                        Log.w(TAG, "Chunk ${chunk.id} done, saving analysis")
                        if (result.activityType != null || result.speakers.isNotEmpty() || result.topics.isNotEmpty()) {
                            analysisRepo.saveFullAnalysis(
                                result = result,
                                chunkDate = chunk.date,
                                chunkStartTime = chunk.startTime,
                                chunkDurationMs = chunk.durationMs
                            )

                            // Find conversation links for this chunk
                            try {
                                val port = settingsRepo.getClaudeBridgePort()
                                val links = conversationLinker.findLinks(
                                    chunkId = chunk.id,
                                    chunkDate = chunk.date,
                                    chunkStartTime = chunk.startTime,
                                    chunkText = result.text,
                                    chunkTopics = result.topics.map { it.name },
                                    chunkSpeakers = result.speakers.map { it.label },
                                    chunkActivity = result.activityType,
                                    bridgeBaseUrl = "http://127.0.0.1:$port"
                                )
                                if (links.isNotEmpty()) {
                                    insightsRepo.saveLinks(links)
                                    analysisState.addLog("Found ${links.size} conversation links")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Linking failed for chunk ${chunk.id}: ${e.message}")
                            }
                        } else {
                            analysisRepo.saveTranscription(result)
                        }
                    }
                } catch (e: CancellationException) {
                    // Worker cancelled — break out, clean up safely below
                    analysisState.addLog("Cancelled during chunk ${chunk.id}")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "ERROR on chunk ${chunk.id}: ${e.message}", e)
                    analysisState.addLog("ERROR on chunk ${chunk.id}: ${e.message}")
                    // Skip this chunk but continue with others
                    // Mark as processed to avoid infinite retry loops
                    withContext(NonCancellable) { chunkDao.markProcessed(chunk.id) }
                }
            }

            withContext(NonCancellable) { pipeline.close() }

            // If cancelled, exit before post-processing
            if (isStopped) {
                notificationUtil.cancelAnalysis()
                analysisState.setIdle()
                return Result.failure()
            }

            notificationUtil.cancelAnalysis()

            // Post-processing: conversation links, daily insight, predictions
            val bridgePort = settingsRepo.getClaudeBridgePort()
            val bridgeBaseUrl = "http://127.0.0.1:$bridgePort"
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())

            try {
                analysisState.addLog("Generating daily insight...")
                insightGenerator.generateDailyInsight(today)
                analysisState.addLog("Daily insight generated")
            } catch (e: Exception) {
                Log.w(TAG, "Daily insight generation failed: ${e.message}")
                analysisState.addLog("Daily insight failed: ${e.message}")
            }

            try {
                analysisState.addLog("Generating predictions...")
                val predictions = predictionEngine.generatePredictions(today, bridgeBaseUrl)
                analysisState.addLog("Generated ${predictions.size} predictions")
            } catch (e: Exception) {
                Log.w(TAG, "Prediction generation failed: ${e.message}")
                analysisState.addLog("Predictions failed: ${e.message}")
            }

            notificationUtil.showInsightsReady()
            analysisState.addLog("All $total chunks processed")
            analysisState.setCompleted()

            return Result.success()
        } catch (e: CancellationException) {
            Log.w(TAG, "AnalysisWorker cancelled")
            analysisState.addLog("Analysis cancelled")
            withContext(NonCancellable) { pipeline.close() }
            notificationUtil.cancelAnalysis()
            analysisState.setIdle()
            return Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: ${e.message}", e)
            analysisState.addLog("FATAL: ${e.message}")
            withContext(NonCancellable) { pipeline.close() }
            notificationUtil.cancelAnalysis()
            analysisState.setError(e.message ?: "Analysis failed")
            return Result.failure()
        }
    }

    companion object {
        private const val TAG = "AnalysisWorker"
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
