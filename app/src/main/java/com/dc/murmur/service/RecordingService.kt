package com.dc.murmur.service

import android.app.Service
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dc.murmur.core.constants.AppConstants
import com.dc.murmur.core.util.BatteryUtil
import com.dc.murmur.core.util.NotificationUtil
import com.dc.murmur.core.util.StorageUtil
import com.dc.murmur.data.local.entity.BatteryLogEntity
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import com.dc.murmur.data.local.entity.SessionEntity
import com.dc.murmur.data.repository.BatteryRepository
import com.dc.murmur.data.repository.RecordingRepository
import com.dc.murmur.data.repository.SettingsRepository
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RecordingService : LifecycleService() {

    companion object {
        private const val TAG = "MURMUR"
    }

    private val recordingRepo: RecordingRepository by inject()
    private val batteryRepo: BatteryRepository by inject()
    private val settingsRepo: SettingsRepository by inject()
    private val storageUtil: StorageUtil by inject()
    private val batteryUtil: BatteryUtil by inject()
    private val notificationUtil: NotificationUtil by inject()

    private var mediaRecorder: MediaRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    private var currentSessionId: String? = null
    private var currentChunkFile: File? = null
    private var chunkStartTime: Long = 0L
    private var chunkBatteryStart: Int = 0
    private var sessionStartTime: Long = 0L

    private var chunkTimerJob: Job? = null
    private var notificationTimerJob: Job? = null
    private var interruptedBy: String? = null
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        storageUtil.ensureDirectoriesExist()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            AppConstants.ACTION_START_RECORDING -> startRecordingSession()
            AppConstants.ACTION_STOP_RECORDING -> stopRecordingSession()
            AppConstants.ACTION_PAUSE_RECORDING -> {
                val reason = intent?.getStringExtra(AppConstants.EXTRA_PAUSE_REASON) ?: "user_paused"
                pauseChunk(reason)
            }
            AppConstants.ACTION_RESUME_RECORDING -> resumeChunk()
        }
        return Service.START_STICKY
    }

    private fun startRecordingSession() {
        Log.d(TAG, "startRecordingSession isRecording=${recordingRepo.isRecording.value}")
        if (recordingRepo.isRecording.value) return
        acquireWakeLock()
        sessionStartTime = System.currentTimeMillis()
        currentSessionId = UUID.randomUUID().toString()
        Log.d(TAG, "session created id=$currentSessionId")

        lifecycleScope.launch {
            val session = SessionEntity(
                id = currentSessionId!!,
                startTime = sessionStartTime
            )
            recordingRepo.startSession(session)
            settingsRepo.setWasRecording(true)
            settingsRepo.setLastSessionId(currentSessionId!!)
        }

        BatteryWorker.schedule(this)
        startForeground(
            AppConstants.RECORDING_NOTIFICATION_ID,
            notificationUtil.buildRecordingNotification(0)
        )
        startNotificationTimer()
        startNewChunk()
    }

    private fun stopRecordingSession() {
        Log.d(TAG, "stopRecordingSession called")
        chunkTimerJob?.cancel()
        notificationTimerJob?.cancel()
        stopCurrentChunkSync()

        // Update state immediately so UI reflects the change
        recordingRepo.setRecordingState(false)
        recordingRepo.setCurrentSession(null)

        BatteryWorker.cancel(this)

        lifecycleScope.launch {
            val session = recordingRepo.getActiveSession().first()
            Log.d(TAG, "active session in DB: ${session?.id ?: "null (race condition)"}")
            if (session != null) {
                recordingRepo.endSession(session.copy(endTime = System.currentTimeMillis()))
                Log.d(TAG, "session ended: ${session.id}")
            }
            settingsRepo.setWasRecording(false)

            releaseAudioFocus()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startNewChunk() {
        val storageCritical = storageUtil.isStorageCritical()
        Log.d(TAG, "startNewChunk storageCritical=$storageCritical")
        if (storageCritical) {
            pauseChunk("low_storage")
            return
        }

        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val chunkFile = storageUtil.getChunkFile(date, now)
        currentChunkFile = chunkFile
        Log.d(TAG, "recording to: ${chunkFile.absolutePath}")
        chunkStartTime = now
        chunkBatteryStart = batteryUtil.getBatteryLevel()

        lifecycleScope.launch {
            batteryRepo.logEvent(
                BatteryLogEntity(
                    timestamp = now,
                    batteryLevel = chunkBatteryStart,
                    isCharging = batteryUtil.isCharging(),
                    temperature = batteryUtil.getTemperatureCelsius(),
                    sessionId = currentSessionId,
                    event = "chunk_start"
                )
            )
        }

        requestAudioFocus()
        mediaRecorder = createMediaRecorder(chunkFile)
        try {
            mediaRecorder?.start()
            isPaused = false
            interruptedBy = null
            scheduleChunkTimer()
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder.start() FAILED", e)
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun scheduleChunkTimer() {
        chunkTimerJob?.cancel()
        chunkTimerJob = lifecycleScope.launch {
            val duration = settingsRepo.getChunkDurationMs()
            delay(duration)
            if (!isPaused) rollChunk()
        }
    }

    private fun rollChunk() {
        stopCurrentChunk(interrupted = false)
        delay500ThenStart()
    }

    private fun pauseChunk(reason: String) {
        Log.d(TAG, "pauseChunk reason=$reason isPaused=$isPaused")
        if (isPaused) return
        isPaused = true
        interruptedBy = reason
        chunkTimerJob?.cancel()
        stopCurrentChunk(interrupted = true)
    }

    private fun resumeChunk() {
        Log.d(TAG, "resumeChunk isPaused=$isPaused")
        if (!isPaused) return
        isPaused = false
        interruptedBy = null
        delay500ThenStart()
    }

    private fun delay500ThenStart() {
        lifecycleScope.launch {
            delay(AppConstants.CHUNK_GAP_MAX_MS)
            startNewChunk()
        }
    }

    private fun stopCurrentChunk(interrupted: Boolean) {
        val recorder = mediaRecorder ?: return
        val file = currentChunkFile ?: return
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "stopCurrentChunk file=${file.name} interrupted=$interrupted")

        try {
            recorder.stop()
        } catch (e: Exception) {
            Log.e(TAG, "recorder.stop() threw", e)
        }
        recorder.release()
        mediaRecorder = null

        val batteryEnd = batteryUtil.getBatteryLevel()
        lifecycleScope.launch {
            saveChunkToDb(file, endTime, batteryEnd, interrupted)
        }
    }

    /** Stops the recorder and saves chunk synchronously — safe to call before stopSelf(). */
    private fun stopCurrentChunkSync() {
        val recorder = mediaRecorder ?: return
        val file = currentChunkFile ?: return
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "stopCurrentChunkSync file=${file.name}")

        try {
            recorder.stop()
        } catch (e: Exception) {
            Log.e(TAG, "recorder.stop() threw (sync)", e)
        }
        recorder.release()
        mediaRecorder = null

        val batteryEnd = batteryUtil.getBatteryLevel()
        kotlinx.coroutines.runBlocking {
            saveChunkToDb(file, endTime, batteryEnd, interrupted = false)
        }
    }

    private suspend fun saveChunkToDb(file: File, endTime: Long, batteryEnd: Int, interrupted: Boolean) {
        Log.d(TAG, "saveChunkToDb file=${file.name} size=${file.length()} exists=${file.exists()} interrupted=$interrupted")
        batteryRepo.logEvent(
            BatteryLogEntity(
                timestamp = endTime,
                batteryLevel = batteryEnd,
                isCharging = batteryUtil.isCharging(),
                temperature = batteryUtil.getTemperatureCelsius(),
                sessionId = currentSessionId,
                event = "chunk_end"
            )
        )

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(chunkStartTime))
        val chunk = RecordingChunkEntity(
            sessionId = currentSessionId ?: "",
            filePath = file.absolutePath,
            fileName = file.name,
            startTime = chunkStartTime,
            endTime = endTime,
            durationMs = endTime - chunkStartTime,
            fileSizeBytes = if (file.exists()) file.length() else 0L,
            batteryLevelStart = chunkBatteryStart,
            batteryLevelEnd = batteryEnd,
            interruptedBy = if (interrupted) interruptedBy else null,
            date = date
        )
        recordingRepo.saveChunk(chunk)
    }

    // --- Audio Focus ---

    private fun requestAudioFocus() {
        val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseChunk("audio_focus_loss")
                AudioManager.AUDIOFOCUS_GAIN -> if (isPaused && interruptedBy == "audio_focus_loss") resumeChunk()
            }
        }
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        audioManager?.requestAudioFocus(audioFocusRequest!!)
    }

    private fun releaseAudioFocus() {
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    // --- MediaRecorder ---

    private fun createMediaRecorder(outputFile: File): MediaRecorder {
        val bitRate = kotlinx.coroutines.runBlocking {
            val quality = settingsRepo.getAudioQuality()
            settingsRepo.getBitRate(quality)
        }
        return MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(AppConstants.AUDIO_SAMPLE_RATE)
            setAudioEncodingBitRate(bitRate)
            setAudioChannels(AppConstants.AUDIO_CHANNELS)
            setOutputFile(outputFile.absolutePath)
            prepare()
        }
    }

    // --- WakeLock ---

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, AppConstants.WAKELOCK_TAG).apply {
            acquire(24 * 60 * 60 * 1000L) // max 24h
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    // --- Notification timer ---

    private fun startNotificationTimer() {
        notificationTimerJob?.cancel()
        notificationTimerJob = lifecycleScope.launch {
            var elapsed = 0L
            while (true) {
                delay(1000)
                elapsed++
                notificationUtil.updateRecordingNotification(elapsed)
            }
        }
    }

    override fun onDestroy() {
        chunkTimerJob?.cancel()
        notificationTimerJob?.cancel()
        mediaRecorder?.release()
        mediaRecorder = null
        releaseAudioFocus()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
