package com.dc.murmur.ai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDecoder {

    data class PcmResult(
        val data: ByteArray,
        val sampleRate: Int,
        val channels: Int
    )

    fun decode(filePath: String): PcmResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)

        val audioTrackIndex = findAudioTrack(extractor)
            ?: throw IllegalArgumentException("No audio track found in $filePath")

        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalArgumentException("No MIME type in audio track")
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val outputStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false

        try {
            while (true) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val pcmBytes = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmBytes)
                        outputStream.write(pcmBytes)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val pcmData = outputStream.toByteArray()

        // Resample to 16kHz mono 16-bit LE if needed
        return PcmResult(
            data = resampleIfNeeded(pcmData, sampleRate, channels),
            sampleRate = 16000,
            channels = 1
        )
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun resampleIfNeeded(pcm: ByteArray, srcRate: Int, srcChannels: Int): ByteArray {
        if (srcRate == 16000 && srcChannels == 1) return pcm

        val shortBuffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(shortBuffer.remaining())
        shortBuffer.get(samples)

        // Downmix to mono if stereo
        val mono = if (srcChannels > 1) {
            ShortArray(samples.size / srcChannels) { i ->
                var sum = 0L
                for (ch in 0 until srcChannels) {
                    sum += samples[i * srcChannels + ch]
                }
                (sum / srcChannels).toInt().toShort()
            }
        } else {
            samples
        }

        // Resample to 16kHz using linear interpolation
        if (srcRate == 16000) {
            val out = ByteBuffer.allocate(mono.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            mono.forEach { out.putShort(it) }
            return out.array()
        }

        val ratio = srcRate.toDouble() / 16000.0
        val outLength = (mono.size / ratio).toInt()
        val resampled = ShortArray(outLength)

        for (i in resampled.indices) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex
            val s0 = mono[srcIndex]
            val s1 = if (srcIndex + 1 < mono.size) mono[srcIndex + 1] else s0
            resampled[i] = (s0 + frac * (s1 - s0)).toInt().toShort()
        }

        val out = ByteBuffer.allocate(resampled.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        resampled.forEach { out.putShort(it) }
        return out.array()
    }
}
