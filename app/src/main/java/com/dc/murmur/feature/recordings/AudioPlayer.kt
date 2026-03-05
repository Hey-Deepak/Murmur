package com.dc.murmur.feature.recordings

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0)
    val progressMs: StateFlow<Int> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _currentFilePath = MutableStateFlow<String?>(null)
    val currentFilePath: StateFlow<String?> = _currentFilePath.asStateFlow()

    fun play(filePath: String) {
        if (_currentFilePath.value == filePath && mediaPlayer != null) {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracking()
            return
        }

        release()
        _currentFilePath.value = filePath

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progressMs.value = 0
                    progressJob?.cancel()
                }
            }
            _durationMs.value = mediaPlayer?.duration ?: 0
            _isPlaying.value = true
            startProgressTracking()
        } catch (e: Exception) {
            _isPlaying.value = false
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
        _progressMs.value = ms
    }

    fun togglePlayPause(filePath: String) {
        if (_isPlaying.value && _currentFilePath.value == filePath) pause() else play(filePath)
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                _progressMs.value = mediaPlayer?.currentPosition ?: 0
                delay(500)
            }
        }
    }

    /**
     * Play only specific time segments of an audio file (for speaker-specific playback).
     * @param filePath Path to the audio file
     * @param segments List of (startMs, endMs) pairs to play sequentially
     */
    fun playSpeakerSegments(filePath: String, segments: List<Pair<Long, Long>>) {
        if (segments.isEmpty()) {
            play(filePath)
            return
        }

        release()
        _currentFilePath.value = filePath

        try {
            val sorted = segments.sortedBy { it.first }
            var segmentIndex = 0

            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                seekTo(sorted[0].first.toInt())
                start()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progressMs.value = 0
                    progressJob?.cancel()
                }
            }
            _durationMs.value = sorted.sumOf { (it.second - it.first).toInt() }
            _isPlaying.value = true

            // Track progress and skip between segments
            progressJob?.cancel()
            progressJob = scope.launch {
                while (_isPlaying.value) {
                    val pos = mediaPlayer?.currentPosition ?: 0
                    _progressMs.value = pos
                    val currentSeg = sorted.getOrNull(segmentIndex)
                    if (currentSeg != null && pos >= currentSeg.second.toInt()) {
                        segmentIndex++
                        val nextSeg = sorted.getOrNull(segmentIndex)
                        if (nextSeg != null) {
                            mediaPlayer?.seekTo(nextSeg.first.toInt())
                        } else {
                            mediaPlayer?.pause()
                            _isPlaying.value = false
                            _progressMs.value = 0
                        }
                    }
                    delay(100)
                }
            }
        } catch (e: Exception) {
            _isPlaying.value = false
        }
    }

    fun release() {
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        _currentFilePath.value = null
        _isPlaying.value = false
        _progressMs.value = 0
        _durationMs.value = 0
    }

    fun destroy() {
        release()
        scope.cancel()
    }
}
