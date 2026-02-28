package com.dc.murmur.ai

import android.content.Context
import android.util.Log
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.ai.nlp.KeywordExtractor
import com.dc.murmur.ai.nlp.TranscriptPostProcessor
import com.dc.murmur.ai.stt.WhisperKitTranscriber
import com.dc.murmur.data.repository.SettingsRepository

data class SpeakerResult(
    val label: String,
    val speakingRatio: Float,
    val turnCount: Int,
    val role: String?,
    val emotionalState: String?
)

data class TopicResult(
    val name: String,
    val relevance: Float,
    val category: String?,
    val keyPoints: List<String>
)

data class AnalysisResult(
    val chunkId: Long,
    val text: String,
    val sentiment: String,
    val sentimentScore: Float,
    val keywords: String,
    val modelUsed: String,
    val activityType: String? = null,
    val activityConfidence: Float? = null,
    val activitySubType: String? = null,
    val speakers: List<SpeakerResult> = emptyList(),
    val topics: List<TopicResult> = emptyList(),
    val behavioralTags: List<String> = emptyList(),
    val keyMoment: String? = null
)

class AnalysisPipeline(
    private val context: Context,
    private val audioDecoder: AudioDecoder,
    private val modelManager: ModelManager,
    private val keywordExtractor: KeywordExtractor,
    private val settingsRepo: SettingsRepository,
    private val claudeAnalyzer: ClaudeCodeAnalyzer,
    private val postProcessor: TranscriptPostProcessor
) {

    private var currentModelId: String? = null
    private var currentTranscriber: Transcriber? = null
    private var currentLanguage: String? = null

    private var onLog: ((String) -> Unit)? = null
    private var onStep: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        onLog = callback
    }

    fun setStepCallback(callback: (String) -> Unit) {
        onStep = callback
    }

    private fun step(message: String) {
        onStep?.invoke(message)
    }

    private fun log(message: String) {
        Log.w(TAG, message)
        onLog?.invoke(message)
    }

    companion object {
        private const val TAG = "AnalysisPipeline"
    }

    suspend fun initialize(onDownloadProgress: (Float) -> Unit = {}) {
        val activeModelId = settingsRepo.getActiveSpeechModel()
        val modelInfo = SpeechModelCatalog.findById(activeModelId)
            ?: SpeechModelCatalog.findById(SpeechModelCatalog.defaultModelId)!!

        val language = settingsRepo.getTranscriptionLanguage()

        log("Active model: ${modelInfo.id} (${modelInfo.provider.name}), language: $language")

        // Close old transcriber if model or language changed
        val needsRecreate = (currentModelId != null && currentModelId != modelInfo.id) ||
            (currentLanguage != null && currentLanguage != language)

        if (needsRecreate) {
            log("Switching from $currentModelId/$currentLanguage to ${modelInfo.id}/$language")
            currentTranscriber?.close()
            currentTranscriber = null
        }

        // WhisperKit manages its own model downloads from HuggingFace
        log("WhisperKit model: ${modelInfo.whisperKitVariant}")
        if (currentTranscriber == null) {
            currentTranscriber = WhisperKitTranscriber(
                context = context,
                whisperKitVariant = modelInfo.whisperKitVariant,
                onDownloadProgress = onDownloadProgress
            )
            log("Created WhisperKit transcriber (variant=${modelInfo.whisperKitVariant})")
        }
        // initialize() triggers model download + load internally
        currentTranscriber!!.initialize(java.io.File(""))
        currentModelId = modelInfo.id
        currentLanguage = language
        log("Transcriber initialized")

        if (claudeAnalyzer.isAvailable()) {
            log("Claude Code bridge detected")
        } else {
            log("Claude Code bridge unavailable — will use on-device fallback")
        }
    }

    fun isReady(): Boolean {
        return currentModelId != null && currentTranscriber != null
    }

    suspend fun processChunk(chunkId: Long, filePath: String): AnalysisResult {
        val transcriber = currentTranscriber
            ?: throw IllegalStateException("Pipeline not initialized")

        val fileName = filePath.substringAfterLast('/')
        log("--- Chunk #$chunkId: $fileName ---")

        // 1. Decode M4A -> PCM
        step("Step 1/4 · Decoding audio (MediaCodec)")
        log("Decoding audio...")
        val pcm = audioDecoder.decode(filePath)
        val durationSec = pcm.data.size / 2f / pcm.sampleRate
        log("Decoded: ${String.format("%.1f", durationSec)}s @ ${pcm.sampleRate}Hz")

        // 2. Transcribe PCM -> text
        val modelName = currentModelId ?: "unknown"
        step("Step 2/4 · Transcribing ($modelName)")
        log("Transcribing with $modelName...")
        val rawText = transcriber.transcribe(pcm.data, pcm.sampleRate)
        if (rawText.isBlank()) {
            log("Result: (empty/silence)")
        } else {
            log("Result: \"${rawText.take(120)}\"")
        }

        // 3. Cleanup transcript — Claude bridge if available, fallback to on-device
        step("Step 3/4 · Cleaning up transcript")
        val text = if (rawText.isBlank()) {
            rawText
        } else {
            val claudeCleanup = if (claudeAnalyzer.isAvailable()) {
                log("Attempting Claude cleanup...")
                claudeAnalyzer.cleanup(rawText)
            } else null

            if (claudeCleanup != null) {
                log("Claude cleanup applied")
                claudeCleanup
            } else {
                log("Using on-device post-processing")
                postProcessor.process(rawText)
            }
        }

        if (text != rawText && text.isNotBlank()) {
            log("Cleaned: \"${text.take(120)}\"")
        }

        // 4. NLP — Claude Code if available, otherwise rule-based extraction
        return if (claudeAnalyzer.isAvailable()) {
            step("Step 4/4 · Analyzing (Claude Code)")
            log("Analyzing with Claude Code (rich mode)...")
            val richAnalysis = claudeAnalyzer.analyzeRich(text)
            if (richAnalysis != null) {
                log("Claude Rich → sentiment=${richAnalysis.sentiment}, activity=${richAnalysis.activityType}, speakers=${richAnalysis.speakers.size}, topics=${richAnalysis.topics.size}")
                AnalysisResult(
                    chunkId = chunkId,
                    text = text,
                    sentiment = richAnalysis.sentiment,
                    sentimentScore = richAnalysis.sentimentScore,
                    keywords = richAnalysis.keywordsJson,
                    modelUsed = "${currentModelId ?: "unknown"}+claude-code-v2",
                    activityType = richAnalysis.activityType,
                    activityConfidence = richAnalysis.activityConfidence,
                    activitySubType = richAnalysis.activitySubType,
                    speakers = richAnalysis.speakers,
                    topics = richAnalysis.topics,
                    behavioralTags = richAnalysis.behavioralTags,
                    keyMoment = richAnalysis.keyMoment
                )
            } else {
                // Fallback to basic analysis
                log("Rich analysis failed, falling back to basic...")
                val analysis = claudeAnalyzer.analyze(text)
                log("Claude → sentiment=${analysis.sentiment} (${String.format("%.2f", analysis.sentimentScore)})")
                AnalysisResult(
                    chunkId = chunkId,
                    text = text,
                    sentiment = analysis.sentiment,
                    sentimentScore = analysis.sentimentScore,
                    keywords = analysis.keywordsJson,
                    modelUsed = "${currentModelId ?: "unknown"}+claude-code"
                )
            }
        } else {
            // Fallback: rule-based keyword extraction only
            step("Step 4/4 · Extracting keywords (on-device)")
            val keywords = keywordExtractor.extract(text)
            val keywordsJson = keywordExtractor.toJson(keywords)
            if (keywords.isNotEmpty()) {
                log("Keywords: ${keywords.take(5).joinToString()}")
            }

            AnalysisResult(
                chunkId = chunkId,
                text = text,
                sentiment = "neutral",
                sentimentScore = 0.5f,
                keywords = keywordsJson,
                modelUsed = currentModelId ?: "unknown"
            )
        }
    }

    fun close() {
        currentTranscriber?.close()
        currentTranscriber = null
        currentModelId = null
        currentLanguage = null
    }
}
