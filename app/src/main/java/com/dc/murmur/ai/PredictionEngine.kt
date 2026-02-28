package com.dc.murmur.ai

import android.util.Log
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.data.local.entity.PredictionEntity
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PredictionEngine(
    private val insightsRepo: InsightsRepository,
    private val peopleRepo: PeopleRepository,
    private val claudeAnalyzer: ClaudeCodeAnalyzer
) {

    companion object {
        private const val TAG = "PredictionEngine"
        private const val MIN_DAYS_FOR_PATTERN = 3
    }

    suspend fun generatePredictions(
        currentDate: String,
        bridgeBaseUrl: String
    ): List<PredictionEntity> {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val cal = Calendar.getInstance()
            val endDate = currentDate
            cal.time = dateFormat.parse(currentDate)!!
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val startDate = dateFormat.format(cal.time)

            // Gather patterns from past 7 days
            val weeklyBreakdown = insightsRepo.getTimeBreakdownRange(startDate, endDate)
            val insights = insightsRepo.getInsightsRange(startDate, endDate)
            val profiles = peopleRepo.getAllProfiles().first()

            if (insights.isEmpty() && weeklyBreakdown.isEmpty()) {
                Log.d(TAG, "Not enough data for predictions")
                return emptyList()
            }

            // Build pattern summary
            val patterns = buildPatternSummary(weeklyBreakdown, insights, profiles)

            // Try Claude for predictions
            if (claudeAnalyzer.isAvailable()) {
                val claudePredictions = predictViaClaude(
                    patterns, currentDate, timeFormat.format(Date()), bridgeBaseUrl
                )
                if (claudePredictions.isNotEmpty()) {
                    // Save predictions
                    claudePredictions.forEach { insightsRepo.savePrediction(it) }
                    return claudePredictions
                }
            }

            // Fallback: heuristic predictions
            val predictions = generateHeuristicPredictions(
                weeklyBreakdown, insights, profiles, currentDate
            )
            predictions.forEach { insightsRepo.savePrediction(it) }
            return predictions
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate predictions: ${e.message}", e)
            return emptyList()
        }
    }

    private fun buildPatternSummary(
        breakdown: List<com.dc.murmur.data.local.dao.ActivityTimeBreakdown>,
        insights: List<com.dc.murmur.data.local.entity.DailyInsightEntity>,
        profiles: List<com.dc.murmur.data.local.entity.VoiceProfileEntity>
    ): String {
        return buildString {
            appendLine("Activity patterns (past 7 days):")
            breakdown.forEach { appendLine("  ${it.activityType}: ${it.totalMs / 60000}m total") }

            appendLine("\nDaily sentiments:")
            insights.forEach {
                appendLine("  ${it.date}: ${it.overallSentiment} (${String.format("%.0f", it.overallSentimentScore * 100)}%)")
            }

            appendLine("\nPeople encountered:")
            profiles.take(10).forEach {
                appendLine("  ${it.label ?: it.voiceId}: ${it.interactionCount} interactions, ${it.totalInteractionMs / 60000}m total")
            }
        }
    }

    private suspend fun predictViaClaude(
        patterns: String,
        currentDate: String,
        currentTime: String,
        bridgeBaseUrl: String
    ): List<PredictionEntity> {
        try {
            val url = URL("$bridgeBaseUrl/predict")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 90_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("patterns", patterns.take(3000))
                put("currentDate", currentDate)
                put("currentTime", currentTime)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                return emptyList()
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val responseObj = JSONObject(responseBody)
            val predictionsArr = responseObj.optJSONArray("predictions") ?: return emptyList()
            val now = System.currentTimeMillis()

            // Tomorrow's date
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = dateFormat.parse(currentDate)!!
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrowDate = dateFormat.format(cal.time)

            return (0 until predictionsArr.length()).mapNotNull { i ->
                val p = predictionsArr.getJSONObject(i)
                val confidence = p.optDouble("confidence", 0.5).toFloat()
                if (confidence < 0.5f) return@mapNotNull null

                PredictionEntity(
                    predictionType = p.optString("predictionType", "routine"),
                    message = p.getString("message"),
                    confidence = confidence,
                    basedOnDays = p.optInt("basedOnDays", 7),
                    triggerTime = p.optString("triggerTime", null)?.toLongOrNull(),
                    date = tomorrowDate,
                    createdAt = now
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Claude predict failed: ${e.message}")
            return emptyList()
        }
    }

    private fun generateHeuristicPredictions(
        breakdown: List<com.dc.murmur.data.local.dao.ActivityTimeBreakdown>,
        insights: List<com.dc.murmur.data.local.entity.DailyInsightEntity>,
        profiles: List<com.dc.murmur.data.local.entity.VoiceProfileEntity>,
        currentDate: String
    ): List<PredictionEntity> {
        val predictions = mutableListOf<PredictionEntity>()
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = dateFormat.parse(currentDate)!!
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowDate = dateFormat.format(cal.time)

        // Routine prediction: dominant activity
        val dominantActivity = breakdown.maxByOrNull { it.totalMs }
        if (dominantActivity != null && insights.size >= MIN_DAYS_FOR_PATTERN) {
            predictions.add(PredictionEntity(
                predictionType = "routine",
                message = "You'll likely spend most of your time ${dominantActivity.activityType.replace("_", " ")} tomorrow, based on your ${insights.size}-day pattern.",
                confidence = 0.7f,
                basedOnDays = insights.size,
                date = tomorrowDate,
                createdAt = now
            ))
        }

        // Sentiment trend
        if (insights.size >= MIN_DAYS_FOR_PATTERN) {
            val avgScore = insights.map { it.overallSentimentScore }.average().toFloat()
            val trend = if (avgScore > 0.6f) "positive" else if (avgScore < 0.4f) "declining" else "stable"
            predictions.add(PredictionEntity(
                predictionType = "habit",
                message = "Your overall mood trend is $trend (avg ${String.format("%.0f", avgScore * 100)}%). ${
                    if (trend == "declining") "Consider taking breaks or doing something enjoyable." else "Keep up the good energy!"
                }",
                confidence = 0.6f,
                basedOnDays = insights.size,
                date = tomorrowDate,
                createdAt = now
            ))
        }

        // Relationship prediction
        val frequentPerson = profiles.maxByOrNull { it.interactionCount }
        if (frequentPerson != null && frequentPerson.interactionCount >= 3) {
            val name = frequentPerson.label ?: "a frequent contact"
            predictions.add(PredictionEntity(
                predictionType = "relationship",
                message = "You've interacted with $name ${frequentPerson.interactionCount} times recently. You'll likely connect again tomorrow.",
                confidence = 0.6f,
                basedOnDays = 7,
                date = tomorrowDate,
                createdAt = now
            ))
        }

        return predictions
    }
}
