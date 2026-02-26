package com.dc.murmur.ai.stt

import android.util.Log
import com.dc.murmur.ai.Transcriber
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperTranscriber : Transcriber {

    companion object {
        private const val TAG = "WhisperTranscriber"
    }

    private var recognizer: OfflineRecognizer? = null

    override suspend fun initialize(modelDir: File) = withContext(Dispatchers.IO) {
        if (recognizer != null) return@withContext

        val encoderFile = File(modelDir, "tiny-encoder.int8.onnx")
        val decoderFile = File(modelDir, "tiny-decoder.int8.onnx")
        val tokensFile = File(modelDir, "tiny-tokens.txt")

        Log.d(TAG, "Model dir: ${modelDir.absolutePath}")
        Log.d(TAG, "Encoder exists: ${encoderFile.exists()} (${encoderFile.length()} bytes)")
        Log.d(TAG, "Decoder exists: ${decoderFile.exists()} (${decoderFile.length()} bytes)")
        Log.d(TAG, "Tokens exists: ${tokensFile.exists()} (${tokensFile.length()} bytes)")

        if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
            throw IllegalStateException(
                "Whisper model files missing in ${modelDir.absolutePath}. " +
                "Contents: ${modelDir.listFiles()?.map { it.name }}"
            )
        }

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderFile.absolutePath,
            decoder = decoderFile.absolutePath,
            language = "hi",     // Hindi — force language for reliable tiny model detection
            task = "transcribe",
            tailPaddings = 1000
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = tokensFile.absolutePath,
            numThreads = 2,
            debug = true,
            provider = "cpu",
            modelType = "whisper"
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        Log.d(TAG, "Creating OfflineRecognizer...")
        recognizer = OfflineRecognizer(config = config)
        Log.d(TAG, "OfflineRecognizer created successfully")
    }

    override suspend fun transcribe(pcmData: ByteArray, sampleRate: Int): String = withContext(Dispatchers.IO) {
        val rec = recognizer ?: throw IllegalStateException("Whisper model not initialized")

        Log.d(TAG, "Transcribing ${pcmData.size} bytes at ${sampleRate}Hz")

        val stream = rec.createStream()
        try {
            // Convert 16-bit little-endian PCM bytes to float samples [-1.0, 1.0]
            val numSamples = pcmData.size / 2
            val samples = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                val low = pcmData[i * 2].toInt() and 0xFF
                val high = pcmData[i * 2 + 1].toInt()
                samples[i] = ((high shl 8) or low).toFloat() / 32768.0f
            }

            Log.d(TAG, "Converted to $numSamples float samples, duration: ${numSamples.toFloat() / sampleRate}s")

            stream.acceptWaveform(samples, sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)

            Log.d(TAG, "Result text: '${result.text}', lang: '${result.lang}'")

            result.text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer?.release()
        recognizer = null
    }
}
