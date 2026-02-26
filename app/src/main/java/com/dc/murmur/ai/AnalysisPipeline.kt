package com.dc.murmur.ai

import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.ai.nlp.KeywordExtractor
import com.dc.murmur.ai.nlp.SentimentAnalyzer
import com.dc.murmur.ai.stt.VoskTranscriber
import com.dc.murmur.ai.stt.WhisperTranscriber
import com.dc.murmur.data.repository.SettingsRepository

data class AnalysisResult(
    val chunkId: Long,
    val text: String,
    val sentiment: String,
    val sentimentScore: Float,
    val keywords: String,
    val modelUsed: String
)

class AnalysisPipeline(
    private val audioDecoder: AudioDecoder,
    private val modelManager: ModelManager,
    private val sentimentAnalyzer: SentimentAnalyzer,
    private val keywordExtractor: KeywordExtractor,
    private val settingsRepo: SettingsRepository,
    private val claudeAnalyzer: ClaudeCodeAnalyzer
) {

    private var currentModelId: String? = null
    private var currentTranscriber: Transcriber? = null

    private var onLog: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        onLog = callback
    }

    private fun log(message: String) {
        onLog?.invoke(message)
    }

    suspend fun initialize(onDownloadProgress: (Float) -> Unit = {}) {
        val activeModelId = settingsRepo.getActiveSpeechModel()
        val modelInfo = SpeechModelCatalog.findById(activeModelId)
            ?: SpeechModelCatalog.findById(SpeechModelCatalog.defaultModelId)!!

        log("Active model: ${modelInfo.id} (${modelInfo.provider.name})")

        // Close old transcriber if model changed
        if (currentModelId != null && currentModelId != modelInfo.id) {
            log("Switching from $currentModelId to ${modelInfo.id}")
            currentTranscriber?.close()
            currentTranscriber = null
        }

        log("Ensuring model downloaded...")
        val modelDir = modelManager.ensureModel(modelInfo.id, onDownloadProgress)
        log("Model ready at: ${modelDir.name}")

        if (currentTranscriber == null) {
            currentTranscriber = when (modelInfo.provider) {
                SpeechProvider.VOSK -> VoskTranscriber()
                SpeechProvider.WHISPER -> WhisperTranscriber()
            }
            log("Created ${modelInfo.provider.name} transcriber")
        }
        currentTranscriber!!.initialize(modelDir)
        log("Transcriber initialized")

        if (claudeAnalyzer.isAvailable()) {
            log("Claude Code detected — skipping sentiment model download")
        } else {
            modelManager.ensureSentimentModel()
            sentimentAnalyzer.initialize(modelManager.sentimentModelPath)
            log("Sentiment model ready (fallback)")
        }
    }

    fun isReady(): Boolean {
        return currentModelId != null && modelManager.isModelReady(currentModelId!!)
    }

    suspend fun processChunk(chunkId: Long, filePath: String): AnalysisResult {
        val transcriber = currentTranscriber
            ?: throw IllegalStateException("Pipeline not initialized")

        val fileName = filePath.substringAfterLast('/')
        log("--- Chunk #$chunkId: $fileName ---")

        // 1. Decode M4A -> PCM
        log("Decoding audio...")
        val pcm = audioDecoder.decode(filePath)
        val durationSec = pcm.data.size / 2f / pcm.sampleRate
        log("Decoded: ${String.format("%.1f", durationSec)}s @ ${pcm.sampleRate}Hz")

        // 2. Transcribe PCM -> text
        log("Transcribing with ${currentModelId ?: "unknown"}...")
        val text = transcriber.transcribe(pcm.data, pcm.sampleRate)
        if (text.isBlank()) {
            log("Result: (empty/silence)")
        } else {
            log("Result: \"${text.take(120)}\"")
        }

        // 3 & 4. NLP — Claude Code if available, otherwise MobileBERT + rules
        return if (claudeAnalyzer.isAvailable()) {
            log("Analyzing with Claude Code...")
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
        } else {
            // Fallback: on-device MobileBERT + rule-based extraction
            val sentiment = sentimentAnalyzer.analyze(text)
            log("Sentiment: ${sentiment.sentiment} (${String.format("%.2f", sentiment.score)})")

            val keywords = keywordExtractor.extract(text)
            val keywordsJson = keywordExtractor.toJson(keywords)
            if (keywords.isNotEmpty()) {
                log("Keywords: ${keywords.take(5).joinToString()}")
            }

            AnalysisResult(
                chunkId = chunkId,
                text = text,
                sentiment = sentiment.sentiment,
                sentimentScore = sentiment.score,
                keywords = keywordsJson,
                modelUsed = currentModelId ?: "unknown"
            )
        }
    }

    fun close() {
        currentTranscriber?.close()
        currentTranscriber = null
        currentModelId = null
        sentimentAnalyzer.close()
    }
}
