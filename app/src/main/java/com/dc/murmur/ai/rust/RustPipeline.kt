package com.dc.murmur.ai.rust

import android.util.Log
import com.dc.murmur.ai.AnalysisResult
import com.dc.murmur.ai.DiarizedSpeakerInfo
import com.dc.murmur.ai.SpeakerResult
import com.dc.murmur.ai.TopicResult
import org.json.JSONObject

/**
 * High-level wrapper around [RustPipelineBridge].
 *
 * Manages the native Pipeline lifecycle and converts JSON results
 * into Kotlin data classes matching the existing [AnalysisResult].
 */
class RustPipeline {

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
     * @param nativeLibDir path to native lib directory for ORT
     * @param whisperModelPath optional path to Whisper model file
     * @param bridgePort Claude bridge port (default 8735)
     */
    /** Last error message from initialization — exposed for UI / logs. */
    var lastError: String? = null
        private set

    fun initialize(
        modelsDir: String,
        nativeLibDir: String? = null,
        whisperModelPath: String? = null,
        bridgePort: Int = 8735
    ): Boolean {
        lastError = null

        if (!isAvailable) {
            lastError = "Native library not loaded: ${RustPipelineBridge.loadError ?: "unknown"}"
            Log.e(TAG, lastError!!)
            return false
        }
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
            put("claude_bridge_url", "http://127.0.0.1:$bridgePort")
        }

        val configStr = config.toString()
        Log.i(TAG, "Creating pipeline with config: $configStr")
        nativePtr = RustPipelineBridge.nativeCreate(configStr)
        if (nativePtr == 0L) {
            lastError = "nativeCreate returned null — bad config or native crash"
            Log.e(TAG, lastError!!)
            return false
        }
        Log.i(TAG, "Pipeline created at ptr=$nativePtr, calling nativeInit...")

        val initResult = RustPipelineBridge.nativeInit(nativePtr)
        if (initResult != 0) {
            lastError = when (initResult) {
                1 -> "nativeInit failed: general error (missing model files in $modelsDir?)"
                2 -> "nativeInit failed: Claude auth error"
                3 -> "nativeInit failed: Claude CLI not found"
                else -> "nativeInit failed: unknown error code $initResult"
            }
            Log.e(TAG, lastError!!)
            RustPipelineBridge.nativeDestroy(nativePtr)
            nativePtr = 0L
            return false
        }

        Log.i(TAG, "Rust pipeline initialized")
        return true
    }

    /** Process an audio file through the full Rust pipeline. */
    fun processChunkFull(chunkId: Long, filePath: String): AnalysisResult? {
        if (nativePtr == 0L) {
            Log.e(TAG, "processChunkFull: nativePtr is 0 (pipeline not initialized)")
            return null
        }

        val file = java.io.File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Chunk $chunkId: file does not exist: $filePath")
            return null
        }
        if (!file.canRead()) {
            Log.e(TAG, "Chunk $chunkId: file not readable: $filePath (exists=${file.exists()}, len=${file.length()})")
            return null
        }

        Log.i(TAG, "Processing chunk $chunkId: $filePath (${file.length()} bytes)")
        val json = RustPipelineBridge.nativeProcess(nativePtr, chunkId.toString(), filePath)
        if (json == null) {
            Log.e(TAG, "Chunk $chunkId: nativeProcess returned null — native pipeline error for $filePath")
            return null
        }
        Log.i(TAG, "Chunk $chunkId: got ${json.length} chars of JSON")
        return parseAnalysisResult(chunkId, json)
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
}
