package com.dc.murmur.ai

import android.content.Context
import com.dc.murmur.core.constants.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class ModelManager(private val context: Context) {

    companion object {
        private const val SENTIMENT_ASSET = "models/sentiment_classifier.tflite"
    }

    private val modelsDir = File(AppConstants.BASE_DIR, "models")

    val sentimentModelPath: String
        get() = File(modelsDir, "sentiment_classifier.tflite").absolutePath

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    init {
        scanDownloadedModels()
    }

    private fun scanDownloadedModels() {
        val states = mutableMapOf<String, ModelDownloadState>()
        for (model in SpeechModelCatalog.models) {
            val dir = File(modelsDir, model.id)
            states[model.id] = if (dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)) {
                ModelDownloadState.Ready
            } else {
                ModelDownloadState.NotDownloaded
            }
        }
        _downloadStates.value = states
    }

    fun getModelDir(modelId: String): File = File(modelsDir, modelId)

    fun isModelReady(modelId: String): Boolean {
        val dir = getModelDir(modelId)
        return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    // Backward-compat helper used by AnalysisPipeline
    suspend fun ensureModel(modelId: String, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        if (isModelReady(modelId)) return@withContext getModelDir(modelId)
        downloadModel(modelId, onProgress)
        getModelDir(modelId)
    }

    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val info = SpeechModelCatalog.findById(modelId)
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        if (isModelReady(modelId)) return@withContext

        modelsDir.mkdirs()

        updateState(modelId, ModelDownloadState.Downloading(0f))

        val extension = if (info.archiveType == "zip") ".zip" else ".tar.bz2"
        val archiveFile = File(modelsDir, "${modelId}$extension")

        try {
            downloadFile(info.downloadUrl, archiveFile) { progress ->
                updateState(modelId, ModelDownloadState.Downloading(progress))
                onProgress(progress)
            }

            when (info.archiveType) {
                "zip" -> extractZip(archiveFile, modelsDir)
                "tar.bz2" -> extractTarBz2(archiveFile, modelsDir)
            }

            archiveFile.delete()
            updateState(modelId, ModelDownloadState.Ready)
        } catch (e: Exception) {
            archiveFile.delete()
            getModelDir(modelId).deleteRecursively()
            updateState(modelId, ModelDownloadState.NotDownloaded)
            throw e
        }
    }

    fun deleteModel(modelId: String) {
        val dir = getModelDir(modelId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        updateState(modelId, ModelDownloadState.NotDownloaded)
    }

    fun isSentimentModelReady(): Boolean {
        return File(modelsDir, "sentiment_classifier.tflite").exists()
    }

    suspend fun ensureSentimentModel(): File = withContext(Dispatchers.IO) {
        val target = File(modelsDir, "sentiment_classifier.tflite")
        if (target.exists()) return@withContext target

        modelsDir.mkdirs()

        try {
            context.assets.open(SENTIMENT_ASSET).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            if (!target.exists()) {
                target.createNewFile()
            }
        }

        target
    }

    private fun updateState(modelId: String, state: ModelDownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }

    private suspend fun downloadFile(
        urlStr: String,
        target: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true

        try {
            connection.connect()
            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun extractTarBz2(archiveFile: File, destDir: File) {
        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                BZip2CompressorInputStream(bis).use { bzis ->
                    TarArchiveInputStream(bzis).use { tais ->
                        var entry = tais.nextEntry
                        while (entry != null) {
                            val outFile = File(destDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    tais.copyTo(fos)
                                }
                            }
                            entry = tais.nextEntry
                        }
                    }
                }
            }
        }
    }
}
