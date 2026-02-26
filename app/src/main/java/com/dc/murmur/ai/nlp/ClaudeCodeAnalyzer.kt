package com.dc.murmur.ai.nlp

import android.util.Log
import com.dc.murmur.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bridges to a Ktor HTTP server running in Termux that wraps the `claude` CLI.
 *
 * Setup:
 *   1. Install Termux, nodejs, and claude-code
 *   2. Push claude-bridge-all.jar to device
 *   3. In Termux: java -jar claude-bridge-all.jar
 *   4. Bridge listens on http://127.0.0.1:8735
 */
class ClaudeCodeAnalyzer(private val settingsRepo: SettingsRepository) {

    data class ClaudeAnalysis(
        val sentiment: String,      // "positive" | "negative" | "neutral"
        val sentimentScore: Float,  // 0.0 – 1.0
        val keywordsJson: String,   // JSON: {"summary":"...","tags":["..."]}
        val available: Boolean      // false = bridge not reachable
    )

    companion object {
        private const val TAG = "ClaudeCodeAnalyzer"
        private const val HEALTH_TIMEOUT_MS = 3_000
        private const val ANALYZE_TIMEOUT_MS = 90_000
    }

    private suspend fun baseUrl(): String {
        val port = settingsRepo.getClaudeBridgePort()
        return "http://127.0.0.1:$port"
    }

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl()}/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = HEALTH_TIMEOUT_MS
            conn.readTimeout = HEALTH_TIMEOUT_MS
            try {
                val code = conn.responseCode
                code == 200
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bridge not reachable: ${e.message}")
            false
        }
    }

    suspend fun analyze(transcript: String): ClaudeAnalysis = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) {
            return@withContext unavailable("Empty transcript")
        }

        val available = isAvailable()
        if (!available) {
            return@withContext unavailable("Claude bridge not reachable at ${baseUrl()}")
        }

        try {
            val url = URL("${baseUrl()}/analyze")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = ANALYZE_TIMEOUT_MS
            conn.readTimeout = ANALYZE_TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("text", transcript)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: "no body"
                } catch (_: Exception) { "unreadable" }
                conn.disconnect()
                Log.w(TAG, "Bridge returned $responseCode: $errorBody")
                return@withContext unavailable("Bridge error: $responseCode")
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            parseResponse(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Bridge call failed: ${e.message}")
            unavailable("Bridge call failed: ${e.message}")
        }
    }

    private fun parseResponse(json: String): ClaudeAnalysis {
        return try {
            val obj = JSONObject(json)
            val sentiment = obj.optString("sentiment", "neutral")
            val score = obj.optDouble("score", 0.5).toFloat().coerceIn(0f, 1f)
            val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }
            val summary = obj.optString("summary", "")

            ClaudeAnalysis(
                sentiment = sentiment,
                sentimentScore = score,
                keywordsJson = buildJson(summary, tags),
                available = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse bridge response, attempting raw parse: ${e.message}")
            parseRawOutput(json)
        }
    }

    private fun parseRawOutput(output: String): ClaudeAnalysis {
        val lines = output.lines()

        val sentiment = lines
            .firstOrNull { it.startsWith("SENTIMENT:") }
            ?.substringAfter("SENTIMENT:").orEmpty().trim().lowercase()
            .let { if (it in listOf("positive", "negative", "neutral")) it else "neutral" }

        val score = lines
            .firstOrNull { it.startsWith("SCORE:") }
            ?.substringAfter("SCORE:").orEmpty().trim()
            .toFloatOrNull()?.coerceIn(0f, 1f)
            ?: if (sentiment == "neutral") 0.5f else 0.75f

        val tags = lines
            .firstOrNull { it.startsWith("TAGS:") }
            ?.substringAfter("TAGS:").orEmpty().trim()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val summaryStartIdx = lines.indexOfFirst { it.startsWith("SUMMARY:") }
        val summary = if (summaryStartIdx >= 0) {
            lines.subList(summaryStartIdx, lines.size)
                .joinToString(" ")
                .substringAfter("SUMMARY:")
                .trim()
        } else {
            output.trim().take(500)
        }

        return ClaudeAnalysis(
            sentiment = sentiment,
            sentimentScore = score,
            keywordsJson = buildJson(summary, tags),
            available = true
        )
    }

    private fun unavailable(reason: String) = ClaudeAnalysis(
        sentiment = "neutral",
        sentimentScore = 0.5f,
        keywordsJson = buildJson(reason, emptyList()),
        available = false
    )

    private fun buildJson(summary: String, tags: List<String>): String {
        return JSONObject().apply {
            put("summary", summary)
            put("tags", JSONArray(tags))
        }.toString()
    }
}
