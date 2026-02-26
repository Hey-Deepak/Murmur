package com.dc.murmur.data.repository

import com.dc.murmur.core.util.StorageUtil
import com.dc.murmur.data.local.dao.RecordingChunkDao
import com.dc.murmur.data.local.dao.SessionDao
import com.dc.murmur.data.local.entity.RecordingChunkEntity
import com.dc.murmur.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingRepository(
    private val chunkDao: RecordingChunkDao,
    private val sessionDao: SessionDao,
    private val storageUtil: StorageUtil
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    fun todayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getTodayChunks(): Flow<List<RecordingChunkEntity>> = chunkDao.getByDate(todayDate())
    fun getTodayCount(): Flow<Int> = chunkDao.getTodayCount(todayDate())
    fun getTodayDurationMs(): Flow<Long> = chunkDao.getTodayDuration(todayDate())
    fun getTodayStorageBytes(): Flow<Long> = chunkDao.getTodaySize(todayDate())
    fun getTotalStorageBytes(): Flow<Long> = chunkDao.getTotalSize()
    fun getAllChunks(): Flow<List<RecordingChunkEntity>> = chunkDao.getAll()
    fun getAllDates(): Flow<List<String>> = chunkDao.getAllDates()
    fun getChunksByDate(date: String): Flow<List<RecordingChunkEntity>> = chunkDao.getByDate(date)
    fun searchChunks(query: String): Flow<List<RecordingChunkEntity>> = chunkDao.search(query)

    fun getActiveSession(): Flow<SessionEntity?> = sessionDao.getActive()

    suspend fun startSession(session: SessionEntity) {
        sessionDao.insert(session)
        _currentSessionId.value = session.id
        _isRecording.value = true
    }

    suspend fun endSession(session: SessionEntity) {
        sessionDao.update(session)
        _currentSessionId.value = null
        _isRecording.value = false
    }

    suspend fun saveChunk(chunk: RecordingChunkEntity): Long = chunkDao.insert(chunk)

    suspend fun getUnprocessedChunks(): List<RecordingChunkEntity> = chunkDao.getUnprocessed()
    suspend fun getUnprocessedChunkCount(): Int = chunkDao.getUnprocessedCount()
    suspend fun markChunkProcessed(id: Long) = chunkDao.markProcessed(id)

    suspend fun deleteChunk(chunk: RecordingChunkEntity) {
        chunkDao.deleteById(chunk.id)
        val file = java.io.File(chunk.filePath)
        if (file.exists()) file.delete()
    }

    suspend fun deleteOldRecordings(beforeDate: String) {
        chunkDao.deleteOlderThan(beforeDate)
        storageUtil.deleteRecordingsOlderThan(beforeDate)
    }

    fun setRecordingState(recording: Boolean) { _isRecording.value = recording }
    fun setCurrentSession(id: String?) { _currentSessionId.value = id }
}
