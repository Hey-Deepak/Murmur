package com.dc.murmur.ai

import java.io.File

interface Transcriber {
    suspend fun initialize(modelDir: File)
    suspend fun transcribe(pcmData: ByteArray, sampleRate: Int = 16000): String
    fun close()
}
