package com.dc.murmur.ai.stt

import android.content.Context
import android.util.Log
import com.argmaxinc.whisperkit.ExperimentalWhisperKit
import com.argmaxinc.whisperkit.TranscriptionResult
import com.argmaxinc.whisperkit.WhisperKit
import com.dc.murmur.ai.Transcriber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@OptIn(ExperimentalWhisperKit::class)
class WhisperKitTranscriber(
    private val context: Context,
    private val whisperKitVariant: String,
    private val onDownloadProgress: (Float) -> Unit = {}
) : Transcriber {

    companion object {
        private const val TAG = "WhisperKitTranscriber"
        private const val CHUNK_BYTES = 3200
        private const val MIN_FRAME_BYTES = 960_000 // 30s at 16kHz 16-bit mono
    }

    private var whisperKit: WhisperKit? = null
    private var closeDeferred: CompletableDeferred<String>? = null
    private val textBuffer = StringBuilder()
    private var needsDeinitialize = false  // true after kit.init(), false after kit.deinitialize()

    private fun onTextOutput(what: Int, result: TranscriptionResult) {
        when (what) {
            WhisperKit.TextOutputCallback.MSG_INIT -> {
                Log.w(TAG, "WhisperKit streaming initialized")
            }
            WhisperKit.TextOutputCallback.MSG_TEXT_OUT -> {
                val text = result.segments.joinToString("") { it.text }
                Log.w(TAG, "MSG_TEXT_OUT: '${text.take(80)}'")
                if (text.isNotBlank()) {
                    synchronized(textBuffer) {
                        if (text.length > textBuffer.length) {
                            // Keep the longest (most complete) transcription
                            textBuffer.clear()
                            textBuffer.append(text)
                        }
                    }
                }
            }
            WhisperKit.TextOutputCallback.MSG_CLOSE -> {
                val text = result.segments.joinToString("") { it.text }
                Log.w(TAG, "MSG_CLOSE: '${text.take(80)}'")
                synchronized(textBuffer) {
                    if (text.isNotBlank()) {
                        textBuffer.clear()
                        textBuffer.append(text)
                    }
                }
                closeDeferred?.complete(textBuffer.toString())
            }
        }
    }

    override suspend fun initialize(modelDir: File) = withContext(Dispatchers.IO) {
        if (whisperKit != null) return@withContext

        Log.w(TAG, "Building WhisperKit with variant: $whisperKitVariant")

        whisperKit = WhisperKit.Builder()
            .setModel(whisperKitVariant)
            .setApplicationContext(context.applicationContext)
            .setCallback(::onTextOutput)
            .setEncoderBackend(WhisperKit.Builder.CPU_ONLY)
            .setDecoderBackend(WhisperKit.Builder.CPU_ONLY)
            .build()

        Log.w(TAG, "Loading model from HuggingFace...")
        whisperKit!!.loadModel().collect { progress ->
            val fraction = progress.fractionCompleted
            Log.w(TAG, "Download progress: ${(fraction * 100).toInt()}%")
            onDownloadProgress(fraction)
        }
        Log.w(TAG, "Model loaded successfully")
    }

    override suspend fun transcribe(pcmData: ByteArray, sampleRate: Int): String = withContext(Dispatchers.IO) {
        val kit = whisperKit ?: throw IllegalStateException("WhisperKit not initialized")

        Log.w(TAG, "Transcribing ${pcmData.size} bytes at ${sampleRate}Hz")

        synchronized(textBuffer) { textBuffer.clear() }
        closeDeferred = CompletableDeferred()

        // Init audio pipeline for this transcription
        kit.init(frequency = sampleRate, channels = 1, duration = 0)
        needsDeinitialize = true
        Log.w(TAG, "Streaming init done, feeding audio...")

        // Feed audio in chunks
        var offset = 0
        var totalSent = 0
        while (offset < pcmData.size) {
            val end = minOf(offset + CHUNK_BYTES, pcmData.size)
            val chunk = pcmData.copyOfRange(offset, end)
            kit.transcribe(chunk)
            totalSent += chunk.size
            offset = end
        }
        Log.w(TAG, "Audio fed: $totalSent bytes, padding to 30s frame...")

        // Pad to 30-second frame boundary
        val remainder = totalSent % MIN_FRAME_BYTES
        if (remainder != 0) {
            val padding = ByteArray(MIN_FRAME_BYTES - remainder)
            kit.transcribe(padding)
        }
        Log.w(TAG, "Padding done, calling deinitialize to flush...")

        // Flush final results
        kit.deinitialize()
        needsDeinitialize = false
        Log.w(TAG, "Deinitialize done, waiting for MSG_CLOSE (timeout 10s)...")

        // Wait for MSG_CLOSE callback with timeout — deinitialize() doesn't always trigger it
        val closeResult = withTimeoutOrNull(10_000L) {
            closeDeferred!!.await()
        }

        val result = if (closeResult != null) {
            Log.w(TAG, "Got MSG_CLOSE result: '${closeResult.take(120)}'")
            closeResult
        } else {
            // MSG_CLOSE never fired — use whatever text we got from MSG_TEXT_OUT
            val fallback = synchronized(textBuffer) { textBuffer.toString() }
            Log.w(TAG, "MSG_CLOSE timeout, using MSG_TEXT_OUT buffer: '${fallback.take(120)}'")
            fallback
        }

        Log.w(TAG, "Transcription complete: '${result.take(120)}'")
        result.trim()
    }

    override fun close() {
        if (needsDeinitialize) {
            try {
                whisperKit?.deinitialize()
            } catch (e: Exception) {
                Log.w(TAG, "Error during WhisperKit cleanup", e)
            }
            needsDeinitialize = false
        }
        whisperKit = null
        closeDeferred = null
    }
}
