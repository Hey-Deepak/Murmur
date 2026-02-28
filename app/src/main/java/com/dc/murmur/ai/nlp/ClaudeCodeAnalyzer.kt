package com.dc.murmur.ai.nlp

import android.util.Log
import com.dc.murmur.ai.SpeakerResult
import com.dc.murmur.ai.TopicResult
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
        val sentiment: String,      // "positive" | "negative" | "neutral" | "anxious" | "frustrated" | "confident" | "hesitant" | "excited"
        val sentimentScore: Float,  // 0.0 – 1.0
        val keywordsJson: String,   // JSON: {"summary":"...","tags":["..."]}
        val available: Boolean      // false = bridge not reachable
    )

    data class ClaudeRichAnalysis(
        val sentiment: String,
        val sentimentScore: Float,
        val keywordsJson: String,
        val activityType: String?,
        val activityConfidence: Float?,
        val activitySubType: String?,
        val speakers: List<SpeakerResult>,
        val topics: List<TopicResult>,
        val behavioralTags: List<String>,
        val keyMoment: String?
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

    suspend fun cleanup(rawTranscript: String): String? = withContext(Dispatchers.IO) {
        if (rawTranscript.isBlank()) return@withContext null

        try {
            val url = URL("${baseUrl()}/cleanup")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = ANALYZE_TIMEOUT_MS
            conn.readTimeout = ANALYZE_TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("text", rawTranscript)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                Log.w(TAG, "Cleanup returned $responseCode")
                return@withContext null
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val obj = JSONObject(responseBody)
            obj.optString("text", null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup call failed: ${e.message}")
            null
        }
    }

    suspend fun analyzeRich(transcript: String): ClaudeRichAnalysis? = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) return@withContext null

        val available = isAvailable()
        if (!available) return@withContext null

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
                conn.disconnect()
                Log.w(TAG, "Rich analyze returned $responseCode")
                return@withContext null
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            parseRichResponse(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Rich analyze failed: ${e.message}")
            null
        }
    }

    private fun parseRichResponse(json: String): ClaudeRichAnalysis? {
        return try {
            val obj = JSONObject(json)

            // Check if this is a rich response (has "activity" field)
            if (!obj.has("activity")) return null

            val sentiment = obj.optString("sentiment", "neutral")
            val score = obj.optDouble("score", 0.5).toFloat().coerceIn(0f, 1f)
            val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }
            val summary = obj.optString("summary", "")

            // Activity
            val activityObj = obj.getJSONObject("activity")
            val activityType = activityObj.optString("type", "idle")
            val activityConfidence = activityObj.optDouble("confidence", 0.5).toFloat()
            val activitySubType = activityObj.optString("subActivity", null)
                ?.takeIf { it.isNotBlank() && it != "null" }

            // Speakers
            val speakersArray = obj.optJSONArray("speakers") ?: JSONArray()
            val speakers = (0 until speakersArray.length()).map { i ->
                val s = speakersArray.getJSONObject(i)
                SpeakerResult(
                    label = s.optString("label", "Speaker ${('A' + i)}"),
                    speakingRatio = s.optDouble("speakingRatio", 0.5).toFloat(),
                    turnCount = s.optInt("turnCount", 1),
                    role = s.optString("role", null)?.takeIf { it.isNotBlank() && it != "null" },
                    emotionalState = s.optString("emotionalState", null)?.takeIf { it.isNotBlank() && it != "null" }
                )
            }

            // Topics
            val topicsArray = obj.optJSONArray("topics") ?: JSONArray()
            val topics = (0 until topicsArray.length()).map { i ->
                val t = topicsArray.getJSONObject(i)
                val keyPointsArr = t.optJSONArray("keyPoints") ?: JSONArray()
                TopicResult(
                    name = t.optString("name", "unknown"),
                    relevance = t.optDouble("relevance", 0.5).toFloat(),
                    category = t.optString("category", null)?.takeIf { it.isNotBlank() && it != "null" },
                    keyPoints = (0 until keyPointsArr.length()).map { j -> keyPointsArr.getString(j) }
                )
            }

            // Behavioral tags
            val behavioralTagsArray = obj.optJSONArray("behavioralTags") ?: JSONArray()
            val behavioralTags = (0 until behavioralTagsArray.length()).map { behavioralTagsArray.getString(it) }

            val keyMoment = obj.optString("keyMoment", null)
                ?.takeIf { it.isNotBlank() && it != "null" }

            ClaudeRichAnalysis(
                sentiment = sentiment,
                sentimentScore = score,
                keywordsJson = buildJson(summary, tags),
                activityType = activityType,
                activityConfidence = activityConfidence,
                activitySubType = activitySubType,
                speakers = speakers,
                topics = topics,
                behavioralTags = behavioralTags,
                keyMoment = keyMoment
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse rich response: ${e.message}")
            null
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

        val validSentiments = listOf(
            "positive", "negative", "neutral", "anxious", "frustrated",
            "confident", "hesitant", "excited"
        )
        val sentiment = lines
            .firstOrNull { it.startsWith("SENTIMENT:") }
            ?.substringAfter("SENTIMENT:").orEmpty().trim().lowercase()
            .let { if (it in validSentiments) it else "neutral" }

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
