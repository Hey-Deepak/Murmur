package com.dc.murmur.ai

import android.content.Context
import android.util.Log
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.ai.nlp.KeywordExtractor
import com.dc.murmur.ai.nlp.TranscriptPostProcessor
import com.dc.murmur.ai.stt.WhisperKitTranscriber
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.SettingsRepository

data class DiarizedSpeakerInfo(
    val speakerIndex: Int,
    val totalMs: Long,
    val ratio: Float,
    val matchedProfileId: Long?,
    val matchedProfileName: String?,
    val matchConfidence: Float?,
    val embedding: FloatArray?,
    // Time ranges within the chunk audio where this speaker talks: [[startMs, endMs], ...]
    val timings: List<Pair<Long, Long>> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiarizedSpeakerInfo) return false
        return speakerIndex == other.speakerIndex && matchedProfileId == other.matchedProfileId
    }
    override fun hashCode() = speakerIndex * 31 + (matchedProfileId?.hashCode() ?: 0)
}

data class SpeakerResult(
    val label: String,
    val speakingRatio: Float,
    val turnCount: Int,
    val role: String?,
    val emotionalState: String?,
    val matchedProfileId: Long? = null
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
    val keyMoment: String? = null,
    val diarizedSpeakers: List<DiarizedSpeakerInfo> = emptyList()
)

class AnalysisPipeline(
    private val context: Context,
    private val audioDecoder: AudioDecoder,
    private val modelManager: ModelManager,
    private val keywordExtractor: KeywordExtractor,
    private val settingsRepo: SettingsRepository,
    private val claudeAnalyzer: ClaudeCodeAnalyzer,
    private val postProcessor: TranscriptPostProcessor,
    private val speakerDiarizer: SpeakerDiarizer,
    private val peopleRepository: PeopleRepository
) {

    private var currentModelId: String? = null
    private var currentTranscriber: Transcriber? = null
    private var currentLanguage: String? = null

    private var onLog: ((String) -> Unit)? = null
    private var onStep: ((String) -> Unit)? = null
    private var onStageTiming: ((String, Long) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        onLog = callback
    }

    fun setStepCallback(callback: (String) -> Unit) {
        onStep = callback
    }

    fun setStageTimingCallback(callback: ((String, Long) -> Unit)?) {
        onStageTiming = callback
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

        val totalSteps = if (speakerDiarizer.isInitialized) 5 else 4
        var stepNum = 0

        // 1. Decode M4A -> PCM
        stepNum++
        step("Step $stepNum/$totalSteps · Decoding audio (MediaCodec)")
        log("Decoding audio...")
        val decodeStart = System.currentTimeMillis()
        val pcm = audioDecoder.decode(filePath)
        onStageTiming?.invoke("decode", System.currentTimeMillis() - decodeStart)
        val durationSec = pcm.data.size / 2f / pcm.sampleRate
        log("Decoded: ${String.format("%.1f", durationSec)}s @ ${pcm.sampleRate}Hz")

        // 2. Speaker diarization (if available)
        var diarizationResult: DiarizationResult? = null
        var diarizedSpeakers = emptyList<DiarizedSpeakerInfo>()
        var speakerContextForClaude: String? = null

        if (speakerDiarizer.isInitialized) {
            stepNum++
            step("Step $stepNum/$totalSteps · Speaker diarization (sherpa-onnx)")
            log("Running speaker diarization...")
            try {
                val diarizeStart = System.currentTimeMillis()
                diarizationResult = speakerDiarizer.diarize(pcm.data, pcm.sampleRate)
                onStageTiming?.invoke("diarize", System.currentTimeMillis() - diarizeStart)
                log("Diarization: ${diarizationResult.speakerCount} speakers, ${diarizationResult.segments.size} segments")

                // Match each speaker against enrolled profiles
                val totalDurationMs = diarizationResult.segments.maxOfOrNull { it.endMs }
                    ?.let { it - (diarizationResult.segments.minOfOrNull { s -> s.startMs } ?: 0) }
                    ?: 1L

                diarizedSpeakers = diarizationResult.speakerEmbeddings.map { (speakerIdx, embedding) ->
                    val speakerSegments = diarizationResult.segments
                        .filter { it.speakerIndex == speakerIdx }
                    val speakerMs = speakerSegments.sumOf { it.endMs - it.startMs }
                    val ratio = speakerMs.toFloat() / totalDurationMs.coerceAtLeast(1)
                    val timings = speakerSegments.map { it.startMs to it.endMs }

                    val matchedProfile = try {
                        peopleRepository.matchSpeaker(embedding)
                    } catch (e: Exception) {
                        Log.w(TAG, "Speaker matching failed: ${e.message}")
                        null
                    }

                    val confidence = if (matchedProfile?.embedding != null) {
                        PeopleRepository.cosineSimilarity(
                            embedding,
                            PeopleRepository.base64ToEmbedding(matchedProfile.embedding!!)
                        )
                    } else null

                    DiarizedSpeakerInfo(
                        speakerIndex = speakerIdx,
                        totalMs = speakerMs,
                        ratio = ratio,
                        matchedProfileId = matchedProfile?.id,
                        matchedProfileName = matchedProfile?.label,
                        matchConfidence = confidence,
                        embedding = embedding,
                        timings = timings
                    )
                }

                // Build context string for Claude
                if (diarizedSpeakers.isNotEmpty()) {
                    speakerContextForClaude = buildString {
                        appendLine("Speaker diarization detected ${diarizedSpeakers.size} speakers:")
                        for (info in diarizedSpeakers) {
                            val name = info.matchedProfileName
                            val confStr = info.matchConfidence?.let { " (${(it * 100).toInt()}% match)" } ?: ""
                            val identity = if (name != null) "$name$confStr" else "unknown"
                            appendLine("  Speaker ${info.speakerIndex}: $identity, ${(info.ratio * 100).toInt()}% of audio")
                        }
                    }
                    log(speakerContextForClaude.trim())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Diarization failed, continuing without it: ${e.message}", e)
                log("Diarization failed: ${e.message}")
            }
        }

        // 3. Transcribe PCM -> text
        stepNum++
        val modelName = currentModelId ?: "unknown"
        step("Step $stepNum/$totalSteps · Transcribing ($modelName)")
        log("Transcribing with $modelName...")
        val transcribeStart = System.currentTimeMillis()
        val rawText = transcriber.transcribe(pcm.data, pcm.sampleRate)
        onStageTiming?.invoke("transcribe", System.currentTimeMillis() - transcribeStart)
        if (rawText.isBlank()) {
            log("Result: (empty/silence)")
        } else {
            log("Result: \"${rawText.take(120)}\"")
        }

        // 4. Cleanup transcript — Claude bridge if available, fallback to on-device
        stepNum++
        step("Step $stepNum/$totalSteps · Cleaning up transcript")
        val cleanupStart = System.currentTimeMillis()
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
        onStageTiming?.invoke("cleanup", System.currentTimeMillis() - cleanupStart)

        if (text != rawText && text.isNotBlank()) {
            log("Cleaned: \"${text.take(120)}\"")
        }

        // 5. NLP — Claude Code if available, otherwise rule-based extraction
        stepNum++
        val analyzeStart = System.currentTimeMillis()
        val analysisResult = if (claudeAnalyzer.isAvailable()) {
            step("Step $stepNum/$totalSteps · Analyzing (Claude Code)")
            log("Analyzing with Claude Code (rich mode)...")
            val richAnalysis = claudeAnalyzer.analyzeRich(text, speakerContextForClaude)
            if (richAnalysis != null) {
                log("Claude Rich → sentiment=${richAnalysis.sentiment}, activity=${richAnalysis.activityType}, speakers=${richAnalysis.speakers.size}, topics=${richAnalysis.topics.size}")

                // Merge Claude speaker results with diarization data
                val mergedSpeakers = mergeSpeakers(richAnalysis.speakers, diarizedSpeakers)

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
                    speakers = mergedSpeakers,
                    topics = richAnalysis.topics,
                    behavioralTags = richAnalysis.behavioralTags,
                    keyMoment = richAnalysis.keyMoment,
                    diarizedSpeakers = diarizedSpeakers
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
                    modelUsed = "${currentModelId ?: "unknown"}+claude-code",
                    diarizedSpeakers = diarizedSpeakers
                )
            }
        } else {
            // Fallback: rule-based keyword extraction only
            step("Step $stepNum/$totalSteps · Extracting keywords (on-device)")
            val keywords = keywordExtractor.extract(text)
            val keywordsJson = keywordExtractor.toJson(keywords)
            if (keywords.isNotEmpty()) {
                log("Keywords: ${keywords.take(5).joinToString()}")
            }

            // Even without Claude, use diarization data to create speaker results
            val speakers = diarizedSpeakers.map { info ->
                SpeakerResult(
                    label = info.matchedProfileName ?: "Speaker ${info.speakerIndex}",
                    speakingRatio = info.ratio,
                    turnCount = diarizationResult?.segments?.count { it.speakerIndex == info.speakerIndex } ?: 1,
                    role = null,
                    emotionalState = null,
                    matchedProfileId = info.matchedProfileId
                )
            }

            AnalysisResult(
                chunkId = chunkId,
                text = text,
                sentiment = "neutral",
                sentimentScore = 0.5f,
                keywords = keywordsJson,
                modelUsed = currentModelId ?: "unknown",
                speakers = speakers,
                diarizedSpeakers = diarizedSpeakers
            )
        }
        onStageTiming?.invoke("analyze", System.currentTimeMillis() - analyzeStart)
        return analysisResult
    }

    private fun mergeSpeakers(
        claudeSpeakers: List<SpeakerResult>,
        diarized: List<DiarizedSpeakerInfo>
    ): List<SpeakerResult> {
        if (diarized.isEmpty()) return claudeSpeakers
        if (claudeSpeakers.isEmpty()) {
            // No Claude speakers, use diarization only
            return diarized.map { info ->
                SpeakerResult(
                    label = info.matchedProfileName ?: "Speaker ${info.speakerIndex}",
                    speakingRatio = info.ratio,
                    turnCount = 1,
                    role = null,
                    emotionalState = null,
                    matchedProfileId = info.matchedProfileId
                )
            }
        }

        // Merge: pair Claude speakers with diarized speakers by order (both sorted by ratio)
        val sortedClaude = claudeSpeakers.sortedByDescending { it.speakingRatio }
        val sortedDiarized = diarized.sortedByDescending { it.ratio }

        return sortedClaude.mapIndexed { index, claudeSpeaker ->
            val matchingDiarized = sortedDiarized.getOrNull(index)
            claudeSpeaker.copy(
                speakingRatio = matchingDiarized?.ratio ?: claudeSpeaker.speakingRatio,
                matchedProfileId = matchingDiarized?.matchedProfileId,
                label = matchingDiarized?.matchedProfileName ?: claudeSpeaker.label
            )
        }
    }

    fun close() {
        currentTranscriber?.close()
        currentTranscriber = null
        currentModelId = null
        currentLanguage = null
        speakerDiarizer.close()
    }
}
