package com.dc.murmur.ai

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dc.murmur.R
import com.dc.murmur.ai.rust.ModelDownloader
import com.dc.murmur.ai.rust.RustPipeline
import com.dc.murmur.ai.rust.RustPipelineBridge
import com.dc.murmur.core.constants.AppConstants
import com.dc.murmur.core.util.BatteryUtil
import com.dc.murmur.core.util.CrashLogger
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

    private val rustPipeline: RustPipeline by inject()
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

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Preparing analysis...")

    private fun createForegroundInfo(text: String, progress: Int = 0, total: Int = 0): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, AppConstants.ANALYSIS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Analyzing recordings")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (total > 0) setProgress(total, progress, false)
                else setProgress(0, 0, true)
            }
            .build()
        return ForegroundInfo(
            AppConstants.ANALYSIS_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result {
        Log.w(TAG, "AnalysisWorker started")

        // Promote to foreground worker — prevents Android from deferring or killing
        // the worker during long-running native operations.
        try {
            setForeground(createForegroundInfo("Starting analysis..."))
        } catch (e: Exception) {
            Log.w(TAG, "Could not promote to foreground: ${e.message}")
        }

        analysisState.clearLog()

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

            // Initialize Rust pipeline (no-op if already warm)
            val initStart = System.currentTimeMillis()
            analysisState.setDownloading()
            analysisState.addLog("Initializing Rust pipeline...")

            rustPipeline.loadLibrary()
            if (!rustPipeline.isAvailable) {
                val err = "Native library failed to load: ${RustPipelineBridge.loadError ?: "unknown"}"
                Log.e(TAG, err)
                analysisState.addLog(err)
                analysisState.setError(err)
                return Result.failure()
            }
            if (!rustPipeline.isInitialized) {
                val modelsDir = applicationContext.filesDir.resolve("models")
                val nativeLibDir = applicationContext.applicationInfo.nativeLibraryDir
                val bridgePort = settingsRepo.getClaudeBridgePort()

                // Download models if missing
                if (!ModelDownloader.modelsExist(modelsDir)) {
                    analysisState.setDownloading()
                    analysisState.addLog("Downloading AI models...")
                    try {
                        setForeground(createForegroundInfo("Downloading AI models..."))
                    } catch (_: Exception) {}

                    val ok = ModelDownloader.ensureModels(modelsDir) { label, downloaded, total ->
                        val pct = if (total > 0) (downloaded * 100 / total) else 0
                        analysisState.addLog("Downloading $label: $pct%")
                    }
                    if (!ok) {
                        val err = "Failed to download required AI models"
                        Log.e(TAG, err)
                        analysisState.addLog(err)
                        analysisState.setError(err)
                        return Result.failure()
                    }
                    analysisState.addLog("All models downloaded")
                }

                val language = settingsRepo.getTranscriptionLanguage()
                val apiKey = settingsRepo.getAnthropicApiKey()
                val success = rustPipeline.initialize(modelsDir.absolutePath, nativeLibDir, bridgePort = bridgePort, language = language, anthropicApiKey = apiKey)
                if (!success) {
                    val err = rustPipeline.lastError ?: "Rust pipeline initialization failed (unknown reason)"
                    Log.e(TAG, err)
                    analysisState.addLog(err)
                    analysisState.setError(err)
                    return Result.failure()
                }
            }

            val initMs = System.currentTimeMillis() - initStart
            analysisState.addLog("Rust pipeline ready (init took ${initMs}ms)")

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
                try {
                    setForeground(createForegroundInfo("Processing $processed / $total", processed, total))
                } catch (_: Exception) {}

                try {
                    // NonCancellable ensures native operations complete before the
                    // coroutine can be cancelled. Native code cannot be safely
                    // interrupted — freeing resources mid-call causes SIGSEGV.
                    withContext(NonCancellable) {
                        val chunkStart = System.currentTimeMillis()
                        Log.w(TAG, "Processing chunk ${chunk.id}: ${chunk.filePath}")

                        // Skip chunks whose audio file was deleted
                        val audioFile = java.io.File(chunk.filePath)
                        if (!audioFile.exists()) {
                            Log.w(TAG, "Chunk ${chunk.id}: file missing, marking processed: ${chunk.filePath}")
                            analysisState.addLog("Chunk ${chunk.id}: file missing, skipping")
                            chunkDao.markProcessed(chunk.id)
                            return@withContext
                        }

                        val result = rustPipeline.processChunkFull(chunk.id, chunk.filePath)
                        if (result == null) {
                            Log.e(TAG, "Chunk ${chunk.id}: Rust pipeline returned null for ${chunk.filePath} (${audioFile.length()} bytes)")
                            analysisState.addLog("Chunk ${chunk.id}: pipeline error (see logcat)")
                            chunkDao.markProcessed(chunk.id)
                            return@withContext
                        }

                        val chunkMs = System.currentTimeMillis() - chunkStart
                        Log.w(TAG, "Chunk ${chunk.id} done in ${chunkMs}ms, saving analysis")
                        analysisState.addLog("Chunk ${chunk.id} processed in ${chunkMs / 1000}s")
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
                    // Worker cancelled (REPLACE policy) — don't close pipeline,
                    // new worker will reuse the warm models immediately
                    analysisState.addLog("Cancelled during chunk ${chunk.id}")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "ERROR on chunk ${chunk.id}: ${e.message}", e)
                    CrashLogger.logException(e, "AnalysisWorker.chunk")
                    analysisState.addLog("ERROR on chunk ${chunk.id}: ${e.message}")
                    // Skip this chunk but continue with others
                    // Mark as processed to avoid infinite retry loops
                    withContext(NonCancellable) { chunkDao.markProcessed(chunk.id) }
                }
            }

            // DON'T destroy pipeline — keep models warm in the Koin singleton.
            // Next worker run skips initialization entirely.

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
            // Worker cancelled — keep pipeline warm for replacement worker
            Log.w(TAG, "AnalysisWorker cancelled")
            analysisState.addLog("Analysis cancelled")
            notificationUtil.cancelAnalysis()
            analysisState.setIdle()
            return Result.failure()
        } catch (e: Exception) {
            // Actual error — destroy pipeline to start fresh next time
            Log.e(TAG, "FATAL: ${e.message}", e)
            CrashLogger.logException(e, "AnalysisWorker.fatal")
            analysisState.addLog("FATAL: ${e.message}")
            withContext(NonCancellable) { rustPipeline.destroy() }
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
