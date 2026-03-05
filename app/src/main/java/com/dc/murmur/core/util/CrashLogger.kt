package com.dc.murmur.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.dc.murmur.core.constants.AppConstants
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

data class CrashEntry(
    val filename: String,
    val timestamp: Long,
    val tag: String,
    val message: String,
    val stackTrace: String,
    val isFixed: Boolean,
    val appVersion: String,
    val androidApi: Int
)

object CrashLogger {

    private const val TAG = "CrashLogger"
    // Primary: internal storage (always writable, no permissions needed)
    private lateinit var internalCrashesDir: File
    // Secondary: external storage (readable by Claude CLI via adb/Termux)
    private val externalCrashesDir = File(AppConstants.LOGS_DIR, "crashes")
    private var appVersion = "unknown"
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var initialized = false

    fun initialize(context: Context) {
        internalCrashesDir = File(context.filesDir, "crashes")
        internalCrashesDir.mkdirs()
        // Also try external — may fail if MANAGE_EXTERNAL_STORAGE not granted yet
        try { externalCrashesDir.mkdirs() } catch (_: Exception) {}

        appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }

        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash(throwable, "uncaught")
            previousHandler?.uncaughtException(thread, throwable)
        }

        initialized = true
        Log.d(TAG, "Initialized, internal dir: ${internalCrashesDir.absolutePath}")

        // Sync: copy any external-only logs to internal (from before this fix)
        syncExternalToInternal()
    }

    fun logCrash(throwable: Throwable, tag: String = "uncaught") {
        writeEntry(throwable, tag)
    }

    fun logException(throwable: Throwable, tag: String) {
        writeEntry(throwable, tag)
    }

    private fun writeEntry(throwable: Throwable, tag: String) {
        val timestamp = System.currentTimeMillis()
        val filename = "crash_$timestamp.json"

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val json = JSONObject().apply {
            put("timestamp", timestamp)
            put("tag", tag)
            put("message", "${throwable.javaClass.simpleName}: ${throwable.message}")
            put("stackTrace", sw.toString())
            put("appVersion", appVersion)
            put("androidApi", Build.VERSION.SDK_INT)
        }

        val text = json.toString(2)

        // Write to internal storage (always works)
        try {
            if (initialized) {
                internalCrashesDir.mkdirs()
                File(internalCrashesDir, filename).writeText(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log to internal storage", e)
        }

        // Also write to external storage (for Claude CLI access)
        try {
            externalCrashesDir.mkdirs()
            File(externalCrashesDir, filename).writeText(text)
        } catch (_: Exception) {
            // External storage may not be available — that's fine
        }
    }

    fun getCrashLogs(): List<CrashEntry> {
        if (!initialized || !internalCrashesDir.exists()) return emptyList()

        return internalCrashesDir.listFiles()
            ?.filter { it.name.startsWith("crash_") && it.name.endsWith(".json") }
            ?.mapNotNull { file -> parseCrashFile(file) }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    private fun parseCrashFile(file: File): CrashEntry? {
        return try {
            val json = JSONObject(file.readText())
            CrashEntry(
                filename = file.name,
                timestamp = json.getLong("timestamp"),
                tag = json.optString("tag", "unknown"),
                message = json.optString("message", ""),
                stackTrace = json.optString("stackTrace", ""),
                isFixed = file.name.endsWith(".fixed.json"),
                appVersion = json.optString("appVersion", "unknown"),
                androidApi = json.optInt("androidApi", 0)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse crash file: ${file.name}", e)
            null
        }
    }

    fun markFixed(filename: String) {
        val fixedName = filename.replace(".json", ".fixed.json")
        // Internal
        File(internalCrashesDir, filename).let { f ->
            if (f.exists()) f.renameTo(File(internalCrashesDir, fixedName))
        }
        // External
        File(externalCrashesDir, filename).let { f ->
            if (f.exists()) f.renameTo(File(externalCrashesDir, fixedName))
        }
    }

    fun deleteAll() {
        listOf(internalCrashesDir, externalCrashesDir).forEach { dir ->
            dir.listFiles()
                ?.filter { it.name.startsWith("crash_") }
                ?.forEach { it.delete() }
        }
    }

    fun deleteFixed() {
        listOf(internalCrashesDir, externalCrashesDir).forEach { dir ->
            dir.listFiles()
                ?.filter { it.name.endsWith(".fixed.json") }
                ?.forEach { it.delete() }
        }
    }

    fun getCrashCount(): Int {
        if (!initialized || !internalCrashesDir.exists()) return 0
        return internalCrashesDir.listFiles()
            ?.count { it.name.startsWith("crash_") && it.name.endsWith(".json") }
            ?: 0
    }

    /** Copy any crash logs that only exist in external storage into internal storage. */
    private fun syncExternalToInternal() {
        try {
            if (!externalCrashesDir.exists()) return
            val internalNames = internalCrashesDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            externalCrashesDir.listFiles()
                ?.filter { it.name.startsWith("crash_") && it.name.endsWith(".json") }
                ?.filter { it.name !in internalNames }
                ?.forEach { extFile ->
                    extFile.copyTo(File(internalCrashesDir, extFile.name), overwrite = false)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync external crash logs", e)
        }
    }
}
