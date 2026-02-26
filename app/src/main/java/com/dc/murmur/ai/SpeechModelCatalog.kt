package com.dc.murmur.ai

enum class SpeechProvider { VOSK, WHISPER }

data class SpeechModelInfo(
    val id: String,
    val provider: SpeechProvider,
    val language: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val archiveType: String  // "zip" or "tar.bz2"
)

object SpeechModelCatalog {
    val models: List<SpeechModelInfo> = listOf(
        SpeechModelInfo(
            id = "vosk-model-small-hi-0.22",
            provider = SpeechProvider.VOSK,
            language = "Hindi",
            sizeBytes = 42_000_000L,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            archiveType = "zip"
        ),
        SpeechModelInfo(
            id = "vosk-model-en-in-0.5",
            provider = SpeechProvider.VOSK,
            language = "English (India)",
            sizeBytes = 1_000_000_000L,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-en-in-0.5.zip",
            archiveType = "zip"
        ),
        SpeechModelInfo(
            id = "sherpa-onnx-whisper-tiny",
            provider = SpeechProvider.WHISPER,
            language = "Hindi + English",
            sizeBytes = 117_000_000L,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
            archiveType = "tar.bz2"
        )
    )

    const val defaultModelId = "vosk-model-small-hi-0.22"

    fun findById(id: String): SpeechModelInfo? = models.find { it.id == id }
}
