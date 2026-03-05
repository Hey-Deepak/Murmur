package com.dc.murmur.ai

import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DiarizedSegment(
    val startMs: Long,
    val endMs: Long,
    val speakerIndex: Int,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiarizedSegment) return false
        return startMs == other.startMs && endMs == other.endMs && speakerIndex == other.speakerIndex
    }

    override fun hashCode(): Int {
        var result = startMs.hashCode()
        result = 31 * result + endMs.hashCode()
        result = 31 * result + speakerIndex
        return result
    }
}

data class DiarizationResult(
    val segments: List<DiarizedSegment>,
    val speakerCount: Int,
    val speakerEmbeddings: Map<Int, FloatArray> // speakerIndex -> averaged embedding
)

class SpeakerDiarizer(private val modelManager: DiarizationModelManager) {

    companion object {
        private const val TAG = "SpeakerDiarizer"
        private const val SAMPLE_RATE = 16000
        // Minimum 2 seconds of 16kHz 16-bit mono PCM = 64000 bytes
        private const val MIN_EMBEDDING_BYTES = 64000
        // Minimum 1.5 seconds of audio samples for per-speaker embedding in diarize()
        private const val MIN_SPEAKER_SAMPLES = (SAMPLE_RATE * 1.5).toInt()

        fun l2Normalize(embedding: FloatArray): FloatArray {
            var sumSq = 0f
            for (v in embedding) sumSq += v * v
            val norm = Math.sqrt(sumSq.toDouble()).toFloat()
            if (norm < 1e-10f) return embedding
            return FloatArray(embedding.size) { embedding[it] / norm }
        }
    }

    private var diarizer: OfflineSpeakerDiarization? = null
    private var embeddingExtractor: SpeakerEmbeddingExtractor? = null

    val isInitialized: Boolean
        get() = diarizer != null

    fun initialize() {
        if (diarizer != null) return

        if (!modelManager.areModelsReady()) {
            Log.w(TAG, "Models not downloaded yet, skipping initialization")
            return
        }

        val segPath = modelManager.getSegmentationModelPath()
        val embPath = modelManager.getEmbeddingModelPath()

        Log.i(TAG, "Initializing diarizer with models: seg=$segPath, emb=$embPath")

        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(segPath),
                numThreads = 2,
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = embPath,
                numThreads = 2,
            ),
            clustering = FastClusteringConfig(numClusters = -1, threshold = 0.45f),
            minDurationOn = 0.5f,
            minDurationOff = 0.5f,
        )

        diarizer = OfflineSpeakerDiarization(config = config)

        embeddingExtractor = SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(
                model = embPath,
                numThreads = 2,
            )
        )

        Log.i(TAG, "Speaker diarizer initialized (sampleRate=${diarizer!!.sampleRate()})")
    }

    fun diarize(pcmData: ByteArray, sampleRate: Int): DiarizationResult {
        val sd = diarizer ?: throw IllegalStateException("Diarizer not initialized")

        // Convert 16-bit PCM bytes to FloatArray
        val samples = pcmBytesToFloat(pcmData)
        Log.i(TAG, "Diarizing ${samples.size} samples (${samples.size / sampleRate.toFloat()}s)")

        val rawSegments: Array<OfflineSpeakerDiarizationSegment> = sd.process(samples)
        Log.i(TAG, "Diarization returned ${rawSegments.size} segments")

        if (rawSegments.isEmpty()) {
            return DiarizationResult(emptyList(), 0, emptyMap())
        }

        // Extract embeddings for each speaker by processing their audio segments
        val speakerIndices = rawSegments.map { it.speaker }.distinct().sorted()
        val speakerEmbeddings = mutableMapOf<Int, FloatArray>()

        val extractor = embeddingExtractor
        if (extractor != null) {
            for (speakerIdx in speakerIndices) {
                val speakerSegments = rawSegments.filter { it.speaker == speakerIdx }.toTypedArray()
                val embedding = extractSpeakerEmbedding(extractor, samples, sampleRate, speakerSegments)
                if (embedding != null) {
                    speakerEmbeddings[speakerIdx] = l2Normalize(embedding)
                }
            }
        }

        val segments = rawSegments.map { seg ->
            DiarizedSegment(
                startMs = (seg.start * 1000).toLong(),
                endMs = (seg.end * 1000).toLong(),
                speakerIndex = seg.speaker,
                embedding = speakerEmbeddings[seg.speaker] ?: FloatArray(0)
            )
        }

        Log.i(TAG, "Diarization complete: ${speakerIndices.size} speakers, ${segments.size} segments")
        for (idx in speakerIndices) {
            val totalMs = segments.filter { it.speakerIndex == idx }.sumOf { it.endMs - it.startMs }
            Log.i(TAG, "  Speaker $idx: ${totalMs}ms, hasEmbedding=${speakerEmbeddings.containsKey(idx)}")
        }

        return DiarizationResult(
            segments = segments,
            speakerCount = speakerIndices.size,
            speakerEmbeddings = speakerEmbeddings
        )
    }

    fun extractEmbedding(pcmData: ByteArray, sampleRate: Int): FloatArray? {
        val extractor = embeddingExtractor ?: return null

        // Require at least 2 seconds of audio for a reliable embedding
        if (pcmData.size < MIN_EMBEDDING_BYTES) {
            Log.w(TAG, "PCM data too short for embedding: ${pcmData.size} bytes (need $MIN_EMBEDDING_BYTES)")
            return null
        }

        val samples = pcmBytesToFloat(pcmData)

        val stream = extractor.createStream()
        stream.acceptWaveform(samples, sampleRate)
        stream.inputFinished()

        return if (extractor.isReady(stream)) {
            l2Normalize(extractor.compute(stream)).also { stream.release() }
        } else {
            stream.release()
            null
        }
    }

    private fun extractSpeakerEmbedding(
        extractor: SpeakerEmbeddingExtractor,
        allSamples: FloatArray,
        sampleRate: Int,
        segments: Array<out OfflineSpeakerDiarizationSegment>
    ): FloatArray? {
        // Concatenate all audio for this speaker (up to 30s to avoid excessive processing)
        val maxSamples = sampleRate * 30
        val speakerSamples = mutableListOf<Float>()

        for (seg in segments) {
            if (speakerSamples.size >= maxSamples) break
            val startIdx = (seg.start * sampleRate).toInt().coerceIn(0, allSamples.size)
            val endIdx = (seg.end * sampleRate).toInt().coerceIn(0, allSamples.size)
            for (i in startIdx until endIdx) {
                if (speakerSamples.size >= maxSamples) break
                speakerSamples.add(allSamples[i])
            }
        }

        if (speakerSamples.size < MIN_SPEAKER_SAMPLES) {
            // Less than 1.5 seconds of audio, too short for reliable embedding
            Log.w(TAG, "Speaker audio too short: ${speakerSamples.size} samples (need $MIN_SPEAKER_SAMPLES)")
            return null
        }

        val stream = extractor.createStream()
        stream.acceptWaveform(speakerSamples.toFloatArray(), sampleRate)
        stream.inputFinished()

        return if (extractor.isReady(stream)) {
            extractor.compute(stream).also { stream.release() }
        } else {
            stream.release()
            null
        }
    }

    private fun pcmBytesToFloat(pcmData: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(shortBuffer.remaining())
        for (i in samples.indices) {
            samples[i] = shortBuffer.get(i) / 32768.0f
        }
        return samples
    }

    fun close() {
        diarizer?.release()
        diarizer = null
        embeddingExtractor?.release()
        embeddingExtractor = null
    }
}
