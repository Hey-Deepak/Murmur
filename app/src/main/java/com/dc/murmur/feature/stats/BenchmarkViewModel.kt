package com.dc.murmur.feature.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dc.murmur.ai.AnalysisPipeline
import com.dc.murmur.ai.rust.BenchmarkComparison
import com.dc.murmur.ai.rust.BenchmarkData
import com.dc.murmur.ai.rust.RustPipeline
import com.dc.murmur.ai.rust.RustPipelineBridge
import com.dc.murmur.ai.rust.StageTiming
import com.dc.murmur.data.local.dao.RecordingChunkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class BenchmarkState {
    IDLE, LOADING_LIBRARY, INITIALIZING, RUNNING, COMPLETED, ERROR
}

class BenchmarkViewModel(
    private val chunkDao: RecordingChunkDao,
    private val analysisPipeline: AnalysisPipeline,
    private val rustPipeline: RustPipeline
) : ViewModel() {

    companion object {
        private const val TAG = "BenchmarkVM"
    }

    private val _state = MutableStateFlow(BenchmarkState.IDLE)
    val state: StateFlow<BenchmarkState> = _state.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private val _results = MutableStateFlow<List<BenchmarkComparison>>(emptyList())
    val results: StateFlow<List<BenchmarkComparison>> = _results.asStateFlow()

    private val _averageSpeedup = MutableStateFlow(0.0)
    val averageSpeedup: StateFlow<Double> = _averageSpeedup.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isLibraryLoaded: Boolean
        get() = RustPipelineBridge.isLoaded

    val libraryLoadError: String?
        get() = RustPipelineBridge.loadError

    init {
        rustPipeline.loadLibrary()
    }

    fun startBenchmark(chunkCount: Int, modelsDir: String, nativeLibDir: String? = null) {
        viewModelScope.launch {
            try {
                _state.value = BenchmarkState.LOADING_LIBRARY
                _results.value = emptyList()
                _errorMessage.value = null

                if (!RustPipelineBridge.isLoaded) {
                    _errorMessage.value = "Rust library not loaded: ${RustPipelineBridge.loadError}"
                    _state.value = BenchmarkState.ERROR
                    return@launch
                }

                // Get processed chunks that still have audio files on disk
                _progress.value = "Finding chunks with audio files..."
                val allChunks = withContext(Dispatchers.IO) {
                    chunkDao.getUnprocessed().ifEmpty {
                        // Fall back to any chunks
                        chunkDao.getAllFilePaths().take(chunkCount * 2).mapNotNull { path ->
                            // We need full entities; query by path isn't available so use getAll
                            null
                        }
                        emptyList()
                    }
                }

                // Filter to chunks whose files exist on disk
                val chunks = withContext(Dispatchers.IO) {
                    val all = chunkDao.getByDateOnce(
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date())
                    ).ifEmpty {
                        // Try getting all chunks, pick recent ones
                        val paths = chunkDao.getAllFilePaths()
                        paths.takeLast(chunkCount * 3).mapNotNull { path ->
                            chunkDao.getById(
                                // We can't query by path, so scan recent
                                0 // placeholder
                            )
                        }
                        emptyList()
                    }
                    all.filter { File(it.filePath).exists() }.takeLast(chunkCount)
                }

                if (chunks.isEmpty()) {
                    _errorMessage.value = "No chunks with audio files found. Record some audio first."
                    _state.value = BenchmarkState.ERROR
                    return@launch
                }

                // Initialize Rust pipeline
                _state.value = BenchmarkState.INITIALIZING
                _progress.value = "Initializing Rust pipeline..."
                val initOk = withContext(Dispatchers.Default) {
                    rustPipeline.initialize(modelsDir, nativeLibDir)
                }
                if (!initOk) {
                    _errorMessage.value = "Failed to initialize Rust pipeline"
                    _state.value = BenchmarkState.ERROR
                    return@launch
                }

                // Initialize Kotlin pipeline if not ready
                var kotlinReady = analysisPipeline.isReady()
                if (!kotlinReady) {
                    _progress.value = "Initializing Kotlin pipeline (WhisperKit)..."
                    Log.i(TAG, "Kotlin pipeline not ready — initializing WhisperKit...")
                    try {
                        withContext(Dispatchers.Default) {
                            analysisPipeline.initialize { progress ->
                                Log.i(TAG, "WhisperKit download: ${(progress * 100).toInt()}%")
                            }
                        }
                        kotlinReady = analysisPipeline.isReady()
                        Log.i(TAG, "Kotlin pipeline initialized: ready=$kotlinReady")
                    } catch (e: Exception) {
                        Log.w(TAG, "Kotlin pipeline init failed: ${e.message}")
                    }
                }
                if (!kotlinReady) {
                    Log.i(TAG, "Kotlin pipeline still not ready — Rust-only benchmark")
                }

                // Run benchmarks
                _state.value = BenchmarkState.RUNNING
                val comparisons = mutableListOf<BenchmarkComparison>()

                for ((index, chunk) in chunks.withIndex()) {
                    _progress.value = "Benchmarking chunk ${index + 1}/${chunks.size}..." +
                        if (!kotlinReady) " (Rust only)" else ""

                    // --- Kotlin pipeline ---
                    val kotlinTimings = mutableListOf<StageTiming>()
                    val kotlinStart = System.currentTimeMillis()

                    if (kotlinReady) {
                        analysisPipeline.setStageTimingCallback { stage, ms ->
                            kotlinTimings.add(StageTiming(stage, ms, true))
                        }

                        try {
                            withContext(Dispatchers.Default) {
                                analysisPipeline.processChunk(chunk.id, chunk.filePath)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Kotlin pipeline failed for chunk ${chunk.id}: ${e.message}")
                            kotlinTimings.add(StageTiming("error", 0, false))
                        }

                        analysisPipeline.setStageTimingCallback(null)
                    }

                    val kotlinTotalMs = if (kotlinReady) System.currentTimeMillis() - kotlinStart else 0L

                    // --- Rust pipeline ---
                    val rustBenchmark: BenchmarkData? = withContext(Dispatchers.Default) {
                        try {
                            rustPipeline.processBenchmark(chunk.id, chunk.filePath)
                        } catch (e: Exception) {
                            Log.w(TAG, "Rust pipeline failed for chunk ${chunk.id}: ${e.message}")
                            null
                        }
                    }

                    if (rustBenchmark != null) {
                        val effectiveKotlinMs = if (kotlinTimings.any { it.stage != "error" }) kotlinTotalMs else 0L
                        val speedup = if (rustBenchmark.totalMs > 0 && effectiveKotlinMs > 0) {
                            effectiveKotlinMs.toDouble() / rustBenchmark.totalMs.toDouble()
                        } else 0.0

                        comparisons.add(BenchmarkComparison(
                            chunkId = chunk.id.toString(),
                            audioDurationSec = rustBenchmark.audioDurationSec,
                            kotlinStages = kotlinTimings,
                            rustStages = rustBenchmark.stages,
                            kotlinTotalMs = effectiveKotlinMs,
                            rustTotalMs = rustBenchmark.totalMs,
                            speedup = speedup,
                            rustPeakMemoryKb = rustBenchmark.peakMemoryKb
                        ))
                    }
                }

                _results.value = comparisons
                _averageSpeedup.value = if (comparisons.isNotEmpty()) {
                    comparisons.map { it.speedup }.average()
                } else 0.0

                _state.value = BenchmarkState.COMPLETED
                _progress.value = "Done — ${comparisons.size} chunks benchmarked"

            } catch (e: Exception) {
                Log.e(TAG, "Benchmark failed", e)
                _errorMessage.value = e.message
                _state.value = BenchmarkState.ERROR
            }
        }
    }

    fun reset() {
        _state.value = BenchmarkState.IDLE
        _results.value = emptyList()
        _progress.value = ""
        _errorMessage.value = null
        _averageSpeedup.value = 0.0
    }

    /** Build a JSON summary of results for clipboard copy. */
    fun resultsToJson(): String {
        val results = _results.value
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"average_speedup\": ${String.format("%.2f", _averageSpeedup.value)},")
        sb.appendLine("  \"chunks\": [")
        results.forEachIndexed { i, c ->
            sb.appendLine("    {")
            sb.appendLine("      \"chunk_id\": \"${c.chunkId}\",")
            sb.appendLine("      \"audio_sec\": ${String.format("%.1f", c.audioDurationSec)},")
            sb.appendLine("      \"kotlin_ms\": ${c.kotlinTotalMs},")
            sb.appendLine("      \"rust_ms\": ${c.rustTotalMs},")
            sb.appendLine("      \"speedup\": ${String.format("%.2f", c.speedup)},")
            sb.appendLine("      \"rust_stages\": [")
            c.rustStages.forEachIndexed { j, s ->
                val comma = if (j < c.rustStages.size - 1) "," else ""
                sb.appendLine("        {\"stage\": \"${s.stage}\", \"ms\": ${s.durationMs}}$comma")
            }
            sb.appendLine("      ],")
            sb.appendLine("      \"kotlin_stages\": [")
            c.kotlinStages.forEachIndexed { j, s ->
                val comma = if (j < c.kotlinStages.size - 1) "," else ""
                sb.appendLine("        {\"stage\": \"${s.stage}\", \"ms\": ${s.durationMs}}$comma")
            }
            sb.appendLine("      ]")
            val comma = if (i < results.size - 1) "," else ""
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        rustPipeline.destroy()
    }
}
