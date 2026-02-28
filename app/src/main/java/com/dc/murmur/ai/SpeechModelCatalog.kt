package com.dc.murmur.ai

enum class SpeechProvider { WHISPERKIT }

data class SpeechModelInfo(
    val id: String,
    val provider: SpeechProvider,
    val language: String,
    val sizeBytes: Long,
    val modelSize: String = "",
    val whisperKitVariant: String = ""
)

object SpeechModelCatalog {
    val models: List<SpeechModelInfo> = listOf(
        SpeechModelInfo(
            id = "whisperkit-openai-tiny-en",
            provider = SpeechProvider.WHISPERKIT,
            language = "English",
            sizeBytes = 60_000_000L,
            modelSize = "tiny",
            whisperKitVariant = "whisperkit-litert/openai_whisper-tiny.en"
        ),
        SpeechModelInfo(
            id = "whisperkit-openai-base-en",
            provider = SpeechProvider.WHISPERKIT,
            language = "English",
            sizeBytes = 145_000_000L,
            modelSize = "base",
            whisperKitVariant = "whisperkit-litert/openai_whisper-base.en"
        ),
        SpeechModelInfo(
            id = "whisperkit-openai-tiny",
            provider = SpeechProvider.WHISPERKIT,
            language = "Hindi + English (Multilingual)",
            sizeBytes = 60_000_000L,
            modelSize = "tiny",
            whisperKitVariant = "whisperkit-litert/openai_whisper-tiny"
        ),
        SpeechModelInfo(
            id = "whisperkit-openai-base",
            provider = SpeechProvider.WHISPERKIT,
            language = "Hindi + English (Multilingual)",
            sizeBytes = 145_000_000L,
            modelSize = "base",
            whisperKitVariant = "whisperkit-litert/openai_whisper-base"
        )
    )

    const val defaultModelId = "whisperkit-openai-tiny"

    fun findById(id: String): SpeechModelInfo? = models.find { it.id == id }
}
