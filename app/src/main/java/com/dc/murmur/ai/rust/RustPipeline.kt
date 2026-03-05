package com.dc.murmur.ai.rust

import android.util.Log
import com.dc.murmur.ai.AnalysisResult
import com.dc.murmur.ai.DiarizedSpeakerInfo
import com.dc.murmur.ai.SpeakerResult
import com.dc.murmur.ai.TopicResult
import com.dc.murmur.data.repository.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

/** Per-stage timing from a pipeline run. */
data class StageTiming(
    val stage: String,
    val durationMs: Long,
    val success: Boolean
)

/** Complete benchmark result for one chunk. */
data class BenchmarkData(
    val chunkId: String,
    val audioDurationSec: Double,
    val stages: List<StageTiming>,
    val totalMs: Long,
    val pipeline: String,
    val peakMemoryKb: Long?
)

/** Side-by-side comparison for one chunk. */
data class BenchmarkComparison(
    val chunkId: String,
    val audioDurationSec: Double,
    val kotlinStages: List<StageTiming>,
    val rustStages: List<StageTiming>,
    val kotlinTotalMs: Long,
    val rustTotalMs: Long,
    val speedup: Double,
    val rustPeakMemoryKb: Long?
)

/**
 * High-level wrapper around [RustPipelineBridge].
 *
 * Manages the native Pipeline lifecycle and converts JSON results
 * into Kotlin data classes matching the existing [AnalysisResult].
 */
class RustPipeline(private val settingsRepo: SettingsRepository) {

    companion object {
        private const val TAG = "RustPipeline"
    }

    private var nativePtr: Long = 0L

    val isAvailable: Boolean
        get() = RustPipelineBridge.isLoaded

    val isInitialized: Boolean
        get() = nativePtr != 0L && RustPipelineBridge.nativeIsReady(nativePtr) == 1

    /**
     * Load the native library (no-op if already loaded).
     * Call this before any other method.
     */
    fun loadLibrary() {
        RustPipelineBridge.load()
    }

    /**
     * Create and initialize the native pipeline.
     *
     * @param modelsDir path to ONNX model files on device
     * @param whisperModelPath optional path to Whisper model file
     */
    fun initialize(modelsDir: String, nativeLibDir: String? = null, whisperModelPath: String? = null): Boolean {
        if (!isAvailable) return false
        if (nativePtr != 0L) {
            destroy()
        }

        val ortLibPath = if (nativeLibDir != null) {
            "$nativeLibDir/libonnxruntime_v23.so"
        } else {
            "libonnxruntime_v23.so"
        }

        val config = JSONObject().apply {
            put("models_dir", modelsDir)
            put("ort_lib_path", ortLibPath)
            if (whisperModelPath != null) put("whisper_model_path", whisperModelPath)
            put("whisper_model_size", "tiny")
            put("language", "en")
            put("num_threads", 2)
        }

        val configStr = config.toString()
        Log.i(TAG, "Creating pipeline with config: $configStr")
        nativePtr = RustPipelineBridge.nativeCreate(configStr)
        if (nativePtr == 0L) {
            Log.e(TAG, "Failed to create native pipeline (nativeCreate returned 0)")
            return false
        }
        Log.i(TAG, "Pipeline created at ptr=$nativePtr, calling nativeInit...")

        val initResult = RustPipelineBridge.nativeInit(nativePtr)
        if (initResult != 0) {
            Log.e(TAG, "nativeInit failed: error code $initResult (1=ERROR, 2=CLAUDE_AUTH, 3=CLAUDE_NOT_FOUND)")
            RustPipelineBridge.nativeDestroy(nativePtr)
            nativePtr = 0L
            return false
        }

        Log.i(TAG, "Rust pipeline initialized")
        return true
    }

    /** Process an audio file through the full Rust pipeline. */
    fun processChunkFull(chunkId: Long, filePath: String): AnalysisResult? {
        if (nativePtr == 0L) return null
        val json = RustPipelineBridge.nativeProcess(nativePtr, chunkId.toString(), filePath)
            ?: return null
        return parseAnalysisResult(chunkId, json)
    }

    /** Process pre-decoded PCM through Rust stages 2-5 (hybrid mode). */
    fun processChunkPcm(chunkId: Long, pcmFloat: FloatArray): AnalysisResult? {
        if (nativePtr == 0L) return null
        val json = RustPipelineBridge.nativeProcessPcm(nativePtr, chunkId.toString(), pcmFloat)
            ?: return null
        return parseAnalysisResult(chunkId, json)
    }

    /** Run benchmark on an audio file. Returns per-stage timing data. */
    fun processBenchmark(chunkId: Long, filePath: String): BenchmarkData? {
        if (nativePtr == 0L) return null
        val json = RustPipelineBridge.nativeProcessBenchmark(nativePtr, chunkId.toString(), filePath)
            ?: return null
        return parseBenchmarkData(json)
    }

    /** Set speaker profiles for matching. */
    fun setProfiles(profilesJson: String): Boolean {
        if (nativePtr == 0L) return false
        return RustPipelineBridge.nativeSetProfiles(nativePtr, profilesJson) == 0
    }

    /** Extract a speaker embedding from PCM data. Returns base64 string. */
    fun extractEmbedding(pcmFloat: FloatArray): String? {
        if (nativePtr == 0L) return null
        return RustPipelineBridge.nativeExtractEmbedding(nativePtr, pcmFloat)
    }

    fun destroy() {
        if (nativePtr != 0L) {
            RustPipelineBridge.nativeDestroy(nativePtr)
            nativePtr = 0L
        }
    }

    // --- PCM conversion ---

    /** Convert 16-bit LE PCM ByteArray to normalized f32 FloatArray. */
    fun pcmBytesToFloat(pcmBytes: ByteArray): FloatArray {
        val numSamples = pcmBytes.size / 2
        val floats = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt()
            val sample16 = (hi shl 8) or lo
            floats[i] = sample16 / 32768.0f
        }
        return floats
    }

    // --- JSON parsing ---

    private fun parseAnalysisResult(chunkId: Long, json: String): AnalysisResult? {
        return try {
            val obj = JSONObject(json)

            val speakers = mutableListOf<SpeakerResult>()
            obj.optJSONArray("speakers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    speakers.add(SpeakerResult(
                        label = s.optString("label", "Speaker $i"),
                        speakingRatio = s.optDouble("speaking_ratio", 0.0).toFloat(),
                        turnCount = s.optInt("turn_count", 0),
                        role = s.optString("role", null),
                        emotionalState = s.optString("emotional_state", null),
                        matchedProfileId = null
                    ))
                }
            }

            val topics = mutableListOf<TopicResult>()
            obj.optJSONArray("topics")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    val keyPoints = mutableListOf<String>()
                    t.optJSONArray("key_points")?.let { kp ->
                        for (j in 0 until kp.length()) keyPoints.add(kp.getString(j))
                    }
                    topics.add(TopicResult(
                        name = t.optString("name", ""),
                        relevance = t.optDouble("relevance", 0.0).toFloat(),
                        category = t.optString("category", null),
                        keyPoints = keyPoints
                    ))
                }
            }

            val behavioralTags = mutableListOf<String>()
            obj.optJSONArray("behavioral_tags")?.let { arr ->
                for (i in 0 until arr.length()) behavioralTags.add(arr.getString(i))
            }

            val diarizedSpeakers = mutableListOf<DiarizedSpeakerInfo>()
            obj.optJSONArray("diarized_speakers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val ds = arr.getJSONObject(i)
                    val timings = mutableListOf<Pair<Long, Long>>()
                    ds.optJSONArray("time_ranges")?.let { tr ->
                        for (j in 0 until tr.length()) {
                            val range = tr.getJSONArray(j)
                            timings.add(range.getLong(0) to range.getLong(1))
                        }
                    }
                    diarizedSpeakers.add(DiarizedSpeakerInfo(
                        speakerIndex = ds.optInt("speaker_index", i),
                        totalMs = ds.optLong("total_speaking_ms", 0),
                        ratio = ds.optDouble("speaking_ratio", 0.0).toFloat(),
                        matchedProfileId = null,
                        matchedProfileName = ds.optString("matched_name", null),
                        matchConfidence = ds.optDouble("match_confidence", -1.0).toFloat().let {
                            if (it < 0) null else it
                        },
                        embedding = null,
                        timings = timings
                    ))
                }
            }

            AnalysisResult(
                chunkId = chunkId,
                text = obj.optString("text", ""),
                sentiment = obj.optString("sentiment", "neutral"),
                sentimentScore = obj.optDouble("sentiment_score", 0.5).toFloat(),
                keywords = obj.optString("keywords_json", "[]"),
                modelUsed = obj.optString("model_used", "murmur-rs"),
                activityType = obj.optString("activity_type", null),
                activityConfidence = obj.optDouble("activity_confidence", 0.0).toFloat(),
                activitySubType = obj.optString("activity_sub_type", null),
                speakers = speakers,
                topics = topics,
                behavioralTags = behavioralTags,
                keyMoment = obj.optString("key_moment", null),
                diarizedSpeakers = diarizedSpeakers
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Rust analysis JSON: ${e.message}")
            null
        }
    }

    private fun parseBenchmarkData(json: String): BenchmarkData? {
        return try {
            val obj = JSONObject(json)

            val stages = mutableListOf<StageTiming>()
            obj.optJSONArray("stages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    stages.add(StageTiming(
                        stage = s.getString("stage"),
                        durationMs = s.getLong("duration_ms"),
                        success = s.optBoolean("success", true)
                    ))
                }
            }

            BenchmarkData(
                chunkId = obj.getString("chunk_id"),
                audioDurationSec = obj.getDouble("audio_duration_sec"),
                stages = stages,
                totalMs = obj.getLong("total_ms"),
                pipeline = obj.getString("pipeline"),
                peakMemoryKb = if (obj.has("peak_memory_kb") && !obj.isNull("peak_memory_kb"))
                    obj.getLong("peak_memory_kb") else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse benchmark JSON: ${e.message}")
            null
        }
    }
}
