package com.dc.murmur.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DiarizationModelManager(private val context: Context) {

    companion object {
        private const val TAG = "DiarizationModelMgr"
        private const val MODELS_DIR = "sherpa-models"

        const val SEGMENTATION_MODEL = "segmentation.onnx"
        const val EMBEDDING_MODEL = "embedding.onnx"

        // Direct .onnx downloads (no archive extraction needed)
        // Segmentation: HuggingFace mirror of sherpa-onnx pyannote segmentation 3.0
        private const val SEGMENTATION_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/main/model.onnx"

        // Embedding: GitHub release (direct .onnx, ~28MB)
        private const val EMBEDDING_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
    }

    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    fun getSegmentationModelPath(): String =
        File(modelsDir, SEGMENTATION_MODEL).absolutePath

    fun getEmbeddingModelPath(): String =
        File(modelsDir, EMBEDDING_MODEL).absolutePath

    fun areModelsReady(): Boolean {
        val seg = File(modelsDir, SEGMENTATION_MODEL)
        val emb = File(modelsDir, EMBEDDING_MODEL)
        return seg.exists() && seg.length() > 0 && emb.exists() && emb.length() > 0
    }

    suspend fun downloadModels(
        onProgress: (model: String, progress: Float) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        if (areModelsReady()) {
            Log.i(TAG, "Models already downloaded")
            return@withContext
        }

        val segFile = File(modelsDir, SEGMENTATION_MODEL)
        if (!segFile.exists() || segFile.length() == 0L) {
            Log.i(TAG, "Downloading segmentation model (~6MB)...")
            downloadFile(SEGMENTATION_URL, segFile) { progress ->
                onProgress(SEGMENTATION_MODEL, progress)
            }
        }

        val embFile = File(modelsDir, EMBEDDING_MODEL)
        if (!embFile.exists() || embFile.length() == 0L) {
            Log.i(TAG, "Downloading embedding model (~28MB)...")
            downloadFile(EMBEDDING_URL, embFile) { progress ->
                onProgress(EMBEDDING_MODEL, progress)
            }
        }

        Log.i(TAG, "All diarization models ready")
    }

    private fun downloadFile(
        urlStr: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ) {
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        try {
            var currentUrl = urlStr
            var conn: HttpURLConnection

            // Follow redirects manually (GitHub/HuggingFace use multiple redirects)
            var redirectCount = 0
            while (true) {
                conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 120_000
                conn.instanceFollowRedirects = false

                val code = conn.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location == null || ++redirectCount > 10) {
                        throw RuntimeException("Too many redirects or missing Location header for $urlStr")
                    }
                    currentUrl = location
                    Log.d(TAG, "Redirect $redirectCount -> ${currentUrl.take(80)}...")
                    continue
                }

                if (code != 200) {
                    val body = try { conn.errorStream?.bufferedReader()?.readText()?.take(200) } catch (_: Exception) { null }
                    conn.disconnect()
                    throw RuntimeException("HTTP $code for $urlStr: $body")
                }
                break
            }

            val totalBytes = conn.contentLengthLong
            var downloaded = 0L
            Log.i(TAG, "Downloading ${destFile.name}: $totalBytes bytes")

            conn.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloaded.toFloat() / totalBytes)
                        }
                    }
                }
            }

            conn.disconnect()

            if (downloaded == 0L) {
                tmpFile.delete()
                throw RuntimeException("Downloaded 0 bytes for ${destFile.name}")
            }

            tmpFile.renameTo(destFile)
            Log.i(TAG, "Downloaded ${destFile.name} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            tmpFile.delete()
            Log.e(TAG, "Download failed: ${e.message}", e)
            throw e
        }
    }
}
