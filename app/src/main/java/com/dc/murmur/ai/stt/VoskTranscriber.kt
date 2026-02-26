package com.dc.murmur.ai.stt

import com.dc.murmur.ai.Transcriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class VoskTranscriber : Transcriber {

    private var model: Model? = null

    override suspend fun initialize(modelDir: File) = withContext(Dispatchers.IO) {
        if (model == null) {
            model = Model(modelDir.absolutePath)
        }
    }

    override suspend fun transcribe(pcmData: ByteArray, sampleRate: Int): String = withContext(Dispatchers.IO) {
        val currentModel = model ?: throw IllegalStateException("Vosk model not initialized")

        val recognizer = Recognizer(currentModel, sampleRate.toFloat())
        try {
            val bufferSize = 4096
            var offset = 0

            while (offset < pcmData.size) {
                val end = minOf(offset + bufferSize, pcmData.size)
                val chunk = pcmData.copyOfRange(offset, end)
                recognizer.acceptWaveForm(chunk, chunk.size)
                offset = end
            }

            val finalResult = recognizer.finalResult
            val json = JSONObject(finalResult)
            json.optString("text", "")
        } finally {
            recognizer.close()
        }
    }

    override fun close() {
        model?.close()
        model = null
    }
}
