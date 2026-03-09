package com.dc.murmur.ai.rust

import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads ONNX and Whisper models required by the Rust pipeline.
 * Uses HttpURLConnection to follow redirects (HuggingFace/GitHub).
 */
object ModelDownloader {

    private const val TAG = "ModelDownloader"

    private const val SEGMENTATION_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/main/model.onnx"
    private const val EMBEDDING_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
    private const val WHISPER_URL_TEMPLATE =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-{size}.bin"

    const val SEGMENTATION_FILENAME = "segmentation.onnx"
    const val EMBEDDING_FILENAME = "embedding.onnx"

    fun whisperFilename(size: String) = "ggml-$size.bin"

    /** Check if all required models exist in the directory. */
    fun modelsExist(modelsDir: File, whisperSize: String = "tiny"): Boolean {
        val seg = File(modelsDir, SEGMENTATION_FILENAME)
        val emb = File(modelsDir, EMBEDDING_FILENAME)
        val whisper = File(modelsDir, whisperFilename(whisperSize))
        return seg.exists() && seg.length() > 0 &&
                emb.exists() && emb.length() > 0 &&
                whisper.exists() && whisper.length() > 0
    }

    /**
     * Download all missing models. Calls [onProgress] with (filename, bytesDownloaded, totalBytes).
     * Returns true if all models are present after this call.
     */
    fun ensureModels(
        modelsDir: File,
        whisperSize: String = "tiny",
        onProgress: ((String, Long, Long) -> Unit)? = null
    ): Boolean {
        modelsDir.mkdirs()

        val tasks = mutableListOf<Triple<String, String, String>>() // (url, filename, label)

        if (!fileValid(File(modelsDir, SEGMENTATION_FILENAME))) {
            tasks.add(Triple(SEGMENTATION_URL, SEGMENTATION_FILENAME, "segmentation model"))
        }
        if (!fileValid(File(modelsDir, EMBEDDING_FILENAME))) {
            tasks.add(Triple(EMBEDDING_URL, EMBEDDING_FILENAME, "embedding model"))
        }
        val whisperFile = whisperFilename(whisperSize)
        if (!fileValid(File(modelsDir, whisperFile))) {
            val url = WHISPER_URL_TEMPLATE.replace("{size}", whisperSize)
            tasks.add(Triple(url, whisperFile, "whisper $whisperSize"))
        }

        if (tasks.isEmpty()) {
            Log.i(TAG, "All models already present in $modelsDir")
            return true
        }

        for ((url, filename, label) in tasks) {
            Log.i(TAG, "Downloading $label from $url")
            try {
                downloadFile(url, File(modelsDir, filename)) { downloaded, total ->
                    onProgress?.invoke(label, downloaded, total)
                }
                Log.i(TAG, "$label download complete")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $label: ${e.message}", e)
                return false
            }
        }

        return modelsExist(modelsDir, whisperSize)
    }

    private fun fileValid(f: File) = f.exists() && f.length() > 0

    private fun downloadFile(
        urlStr: String,
        dest: File,
        onProgress: (Long, Long) -> Unit
    ) {
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        try {
            var conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true

            // Manual redirect follow for cross-protocol redirects (http->https)
            var redirects = 0
            while (redirects < 5) {
                val code = conn.responseCode
                if (code in 301..308) {
                    val location = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    conn = URL(location).openConnection() as HttpURLConnection
                    conn.connectTimeout = 30_000
                    conn.readTimeout = 60_000
                    conn.instanceFollowRedirects = true
                    redirects++
                } else {
                    break
                }
            }

            if (conn.responseCode != 200) {
                throw java.io.IOException("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }

            val totalBytes = conn.contentLengthLong
            var downloaded = 0L
            val buffer = ByteArray(8192)

            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        onProgress(downloaded, totalBytes)
                    }
                }
            }
            conn.disconnect()

            // Atomic rename
            tmp.renameTo(dest)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
