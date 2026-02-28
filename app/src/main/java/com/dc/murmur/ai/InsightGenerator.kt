package com.dc.murmur.ai

import android.util.Log
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.data.local.entity.DailyInsightEntity
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import com.dc.murmur.data.repository.RecordingRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class InsightGenerator(
    private val insightsRepo: InsightsRepository,
    private val analysisRepo: AnalysisRepository,
    private val recordingRepo: RecordingRepository,
    private val peopleRepo: PeopleRepository,
    private val claudeAnalyzer: ClaudeCodeAnalyzer
) {

    companion object {
        private const val TAG = "InsightGenerator"
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

            // Build people summary
            val allProfiles = peopleRepo.getAllProfiles().first()
            val peopleStr = allProfiles.take(10).joinToString(", ") {
                "${it.label ?: it.voiceId}: ${it.totalInteractionMs / 1000}s"
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

            // Fallback: generate basic insight locally
            val avgScore = dayTranscriptions.map { it.sentimentScore }.average().toFloat()
            val overallSentiment = when {
                avgScore >= 0.7f -> "positive"
                avgScore <= 0.3f -> "negative"
                else -> "neutral"
            }

            val timeBreakdownJson = JSONObject().apply {
                activities.forEach { put(it.activityType, it.totalMs / 60000) }
            }.toString()

            val topTopicsJson = JSONArray(topicNames.take(5).map { it.key }).toString()

            val insight = DailyInsightEntity(
                date = date,
                timelineJson = "[]",
                timeBreakdownJson = timeBreakdownJson,
                peopleSummaryJson = "[]",
                topTopics = topTopicsJson,
                highlight = dayTranscriptions.firstOrNull()?.keyMoment,
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
    ): DailyInsightEntity? {
        // This would call the /daily-insight endpoint on the bridge
        // For now, return null to use fallback
        return null
    }
}
