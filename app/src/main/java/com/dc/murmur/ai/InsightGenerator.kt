package com.dc.murmur.ai

import android.util.Log
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.data.local.entity.DailyInsightEntity
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.RecordingRepository
import com.dc.murmur.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class InsightGenerator(
    private val insightsRepo: InsightsRepository,
    private val analysisRepo: AnalysisRepository,
    private val recordingRepo: RecordingRepository,
    private val peopleRepo: PeopleRepository,
    private val claudeAnalyzer: ClaudeCodeAnalyzer,
    private val settingsRepo: SettingsRepository
) {

    companion object {
        private const val TAG = "InsightGenerator"
        private const val TIMEOUT_MS = 90_000
    }

    suspend fun generateDailyInsight(date: String): DailyInsightEntity? {
        try {
            // Check if already generated
            val existing = insightsRepo.getDailyInsight(date)
            if (existing != null) {
                Log.d(TAG, "Daily insight already exists for $date")
                return existing
            }

            // Gather data
            val transcriptions = analysisRepo.getAllTranscriptionsWithChunks().first()
            val dayTranscriptions = transcriptions.filter { it.date == date }

            if (dayTranscriptions.isEmpty()) {
                Log.d(TAG, "No transcriptions for $date")
                return null
            }

            val activities = insightsRepo.getTimeBreakdown(date)
            val chunks = recordingRepo.getChunksByDate(date).first()
            val totalRecordedMs = chunks.sumOf { it.durationMs }
            val totalChunks = dayTranscriptions.size

            // Build activity summary
            val activitiesStr = activities.joinToString(", ") { "${it.activityType}: ${it.totalMs / 1000}s" }
                .ifBlank { "No activities detected" }

            // Build topics summary
            val topicNames = dayTranscriptions
                .mapNotNull { it.topicsSummary }
                .flatMap { json ->
                    try {
                        val arr = JSONArray(json)
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (_: Exception) { emptyList() }
                }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(10)
            val topicsStr = topicNames.joinToString(", ") { "${it.key} (${it.value}x)" }
                .ifBlank { "No topics detected" }

            // Build people summary from tagged profiles with recent activity
            val allProfiles = peopleRepo.getAllProfiles().first()
            val taggedWithActivity = allProfiles
                .filter { it.label != null && it.totalInteractionMs > 0 }
                .sortedByDescending { it.lastSeenAt }
                .take(10)
            val peopleStr = taggedWithActivity.joinToString(", ") {
                "${it.label}: ${it.totalInteractionMs / 1000}s (${it.interactionCount} interactions)"
            }.ifBlank { "No people detected" }

            // Build sentiment summary
            val sentimentsStr = dayTranscriptions.joinToString(", ") {
                "${it.sentiment} (${String.format("%.0f", it.sentimentScore * 100)}%)"
            }

            // Try Claude for daily insight generation
            if (claudeAnalyzer.isAvailable()) {
                val claudeInsight = generateViaClaude(
                    date, activitiesStr, topicsStr, peopleStr, sentimentsStr,
                    totalRecordedMs, totalChunks
                )
                if (claudeInsight != null) {
                    insightsRepo.saveDailyInsight(claudeInsight)
                    return claudeInsight
                }
            }

            // Fallback: generate rich insight locally
            val avgScore = dayTranscriptions.map { it.sentimentScore }.average().toFloat()
            val overallSentiment = when {
                avgScore >= 0.7f -> "positive"
                avgScore >= 0.55f -> "mostly positive"
                avgScore <= 0.3f -> "negative"
                avgScore <= 0.45f -> "mixed"
                else -> "neutral"
            }

            val timeBreakdownJson = JSONObject().apply {
                activities.forEach { put(it.activityType, it.totalMs / 60000) }
            }.toString()

            val topTopicsJson = JSONArray(topicNames.take(5).map { it.key }).toString()

            // Build people summary JSON
            val peopleSummaryJson = JSONArray().apply {
                taggedWithActivity.forEach { profile ->
                    put(JSONObject().apply {
                        put("name", profile.label)
                        put("totalMs", profile.totalInteractionMs)
                        put("interactions", profile.interactionCount)
                    })
                }
            }.toString()

            // Build timeline from activities
            val dayActivities = insightsRepo.getActivitiesByDate(date).first()
            val timelineJson = JSONArray().apply {
                dayActivities.sortedBy { it.startTime }.forEach { act ->
                    put(JSONObject().apply {
                        put("time", java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(act.startTime)))
                        put("activity", act.activityType)
                        put("subActivity", act.subActivity ?: "")
                        put("durationMs", act.durationMs)
                    })
                }
            }.toString()

            // Pick the best highlight: prefer keyMoment, then fall back to best-scored transcription
            val highlight = dayTranscriptions
                .mapNotNull { it.keyMoment }
                .firstOrNull()
                ?: dayTranscriptions
                    .maxByOrNull { it.sentimentScore }
                    ?.let { t ->
                        val summary = try {
                            JSONObject(t.keywords).optString("summary", "").take(120)
                        } catch (_: Exception) { "" }
                        summary.ifBlank { null }
                    }

            val insight = DailyInsightEntity(
                date = date,
                timelineJson = timelineJson,
                timeBreakdownJson = timeBreakdownJson,
                peopleSummaryJson = peopleSummaryJson,
                topTopics = topTopicsJson,
                highlight = highlight,
                overallSentiment = overallSentiment,
                overallSentimentScore = avgScore,
                totalRecordedMs = totalRecordedMs,
                totalAnalyzedChunks = totalChunks,
                generatedAt = System.currentTimeMillis()
            )

            insightsRepo.saveDailyInsight(insight)
            return insight
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily insight: ${e.message}", e)
            return null
        }
    }

    private suspend fun generateViaClaude(
        date: String,
        activities: String,
        topics: String,
        people: String,
        sentiments: String,
        totalRecordedMs: Long,
        totalChunks: Int
    ): DailyInsightEntity? = withContext(Dispatchers.IO) {
        try {
            val port = settingsRepo.getClaudeBridgePort()
            val url = URL("http://127.0.0.1:$port/daily-insight")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("date", date)
                put("activities", activities)
                put("topics", topics)
                put("people", people)
                put("sentiments", sentiments)
                put("totalRecordedMs", totalRecordedMs)
                put("totalChunks", totalChunks)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                Log.w(TAG, "Claude daily-insight returned $responseCode")
                return@withContext null
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val obj = JSONObject(responseBody)
            DailyInsightEntity(
                date = date,
                timelineJson = obj.optString("timelineJson", "[]"),
                timeBreakdownJson = obj.optString("timeBreakdownJson", "{}"),
                peopleSummaryJson = obj.optString("peopleSummaryJson", "[]"),
                topTopics = obj.optString("topTopics", "[]"),
                highlight = obj.optString("highlight", null)?.takeIf { it.isNotBlank() && it != "null" },
                overallSentiment = obj.optString("overallSentiment", "neutral"),
                overallSentimentScore = obj.optDouble("overallSentimentScore", 0.5).toFloat(),
                totalRecordedMs = totalRecordedMs,
                totalAnalyzedChunks = totalChunks,
                generatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Claude daily-insight failed: ${e.message}")
            null
        }
    }
}
