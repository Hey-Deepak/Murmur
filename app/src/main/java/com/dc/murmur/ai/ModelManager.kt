package com.dc.murmur.ai

import android.content.Context
import android.util.Log
import com.argmaxinc.whisperkit.ExperimentalWhisperKit
import com.argmaxinc.whisperkit.WhisperKit
import com.dc.murmur.core.constants.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
    }

    private val modelsDir = File(AppConstants.BASE_DIR, "models")

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    init {
        scanDownloadedModels()
    }

    private fun scanDownloadedModels() {
        val states = mutableMapOf<String, ModelDownloadState>()
        for (model in SpeechModelCatalog.models) {
            val dir = File(modelsDir, model.id)
            val sdkDir = getWhisperKitSdkDir(model)
            val ready = (dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)) ||
                (sdkDir.exists() && sdkDir.isDirectory && (sdkDir.listFiles()?.isNotEmpty() == true))
            states[model.id] = if (ready) ModelDownloadState.Ready else ModelDownloadState.NotDownloaded
        }
        _downloadStates.value = states
    }

    /** Where the WhisperKit SDK stores downloaded model files */
    private fun getWhisperKitSdkDir(model: SpeechModelInfo): File {
        val modelName = model.whisperKitVariant.substringAfter("/")
        return File(context.filesDir, "argmaxinc/models/$modelName")
    }

    fun getModelDir(modelId: String): File = File(modelsDir, modelId)

    fun isModelReady(modelId: String): Boolean {
        val model = SpeechModelCatalog.findById(modelId) ?: return false
        val dir = getModelDir(modelId)
        val sdkDir = getWhisperKitSdkDir(model)
        return (dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)) ||
            (sdkDir.exists() && sdkDir.isDirectory && (sdkDir.listFiles()?.isNotEmpty() == true))
    }

    @OptIn(ExperimentalWhisperKit::class)
    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val info = SpeechModelCatalog.findById(modelId)
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        if (isModelReady(modelId)) return@withContext

        modelsDir.mkdirs()
        updateState(modelId, ModelDownloadState.Downloading(0f))

        try {
            Log.d(TAG, "Downloading WhisperKit model: ${info.whisperKitVariant}")
            val kit = WhisperKit.Builder()
                .setModel(info.whisperKitVariant)
                .setApplicationContext(context.applicationContext)
                .setCallback { _, _ -> }
                .setEncoderBackend(WhisperKit.Builder.CPU_ONLY)
                .setDecoderBackend(WhisperKit.Builder.CPU_ONLY)
                .build()

            try {
                kit.loadModel().collect { progress ->
                    val fraction = progress.fractionCompleted
                    Log.d(TAG, "WhisperKit download: ${(fraction * 100).toInt()}%")
                    updateState(info.id, ModelDownloadState.Downloading(fraction))
                    onProgress(fraction)
                }
            } finally {
                try { kit.deinitialize() } catch (_: Exception) {}
            }

            // Create marker dir so our scan also detects it
            val dir = getModelDir(info.id)
            dir.mkdirs()
            File(dir, ".whisperkit_ready").writeText(info.whisperKitVariant)
            Log.d(TAG, "WhisperKit model ready: ${info.id}")

            updateState(modelId, ModelDownloadState.Ready)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model $modelId", e)
            getModelDir(modelId).deleteRecursively()
            val msg = e.message ?: e.javaClass.simpleName
            updateState(modelId, ModelDownloadState.Error(msg))
            throw e
        }
    }

    fun deleteModel(modelId: String) {
        val model = SpeechModelCatalog.findById(modelId)
        val dir = getModelDir(modelId)
        if (dir.exists()) dir.deleteRecursively()
        // Also clean SDK cache
        if (model != null) {
            val sdkDir = getWhisperKitSdkDir(model)
            if (sdkDir.exists()) sdkDir.deleteRecursively()
        }
        updateState(modelId, ModelDownloadState.NotDownloaded)
    }

    private fun updateState(modelId: String, state: ModelDownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }
}
