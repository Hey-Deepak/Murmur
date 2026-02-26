package com.dc.murmur.core.util

import android.content.Context
import com.dc.murmur.core.constants.AppConstants
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageUtil(private val context: Context) {

    fun ensureDirectoriesExist() {
        AppConstants.RECORDINGS_DIR.mkdirs()
        AppConstants.LOGS_DIR.mkdirs()
    }

    fun getDailyDir(date: String): File {
        val dir = File(AppConstants.RECORDINGS_DIR, date)
        dir.mkdirs()
        return dir
    }

    fun getChunkFile(date: String, timestamp: Long): File {
        val timeStr = SimpleDateFormat("HH-mm-ss", Locale.US).format(Date(timestamp))
        val fileName = "${AppConstants.CHUNK_FILE_PREFIX}${date}_${timeStr}${AppConstants.FILE_EXTENSION}"
        return File(getDailyDir(date), fileName)
    }

    fun getFreeSpaceMb(): Long {
        return AppConstants.RECORDINGS_DIR.freeSpace / (1024 * 1024)
    }

    fun getTotalUsageBytes(): Long {
        return AppConstants.BASE_DIR.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun isStorageCritical(): Boolean = getFreeSpaceMb() < AppConstants.LOW_STORAGE_CRITICAL_MB

    fun isStorageLow(): Boolean = getFreeSpaceMb() < AppConstants.LOW_STORAGE_WARNING_MB

    /** Delete recording files whose date is older than [beforeDate] (YYYY-MM-DD). */
    fun deleteRecordingsOlderThan(beforeDate: String) {
        AppConstants.RECORDINGS_DIR.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name < beforeDate) {
                dir.deleteRecursively()
            }
        }
    }

    /** Remove .m4a files that have no corresponding DB entry (pass known file paths). */
    fun cleanOrphanFiles(knownPaths: Set<String>) {
        AppConstants.RECORDINGS_DIR.walkTopDown()
            .filter { it.isFile && it.extension == "m4a" }
            .forEach { file ->
                if (file.absolutePath !in knownPaths) {
                    file.delete()
                }
            }
    }
}
