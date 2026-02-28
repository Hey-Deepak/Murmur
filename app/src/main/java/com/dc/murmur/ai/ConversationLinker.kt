package com.dc.murmur.ai

import android.util.Log
import com.dc.murmur.ai.nlp.ClaudeCodeAnalyzer
import com.dc.murmur.data.local.entity.ConversationLinkEntity
import com.dc.murmur.data.local.entity.TranscriptionWithChunk
import com.dc.murmur.data.repository.AnalysisRepository
import com.dc.murmur.data.repository.InsightsRepository
import com.dc.murmur.data.repository.PeopleRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ConversationLinker(
    private val insightsRepo: InsightsRepository,
    private val analysisRepo: AnalysisRepository,
    private val peopleRepo: PeopleRepository,
    private val claudeAnalyzer: ClaudeCodeAnalyzer
) {

    companion object {
        private const val TAG = "ConversationLinker"
        private const val MAX_CANDIDATES = 10
    }

    suspend fun findLinks(
        chunkId: Long,
        chunkDate: String,
        chunkStartTime: Long,
        chunkText: String,
        chunkTopics: List<String>,
        chunkSpeakers: List<String>,
        chunkActivity: String?,
        bridgeBaseUrl: String
    ): List<ConversationLinkEntity> {
        try {
            val allTranscriptions = analysisRepo.getAllTranscriptionsWithChunks().first()
            val otherChunks = allTranscriptions.filter { it.chunkId != chunkId }

            if (otherChunks.isEmpty()) return emptyList()

            // Find candidates by various criteria
            val candidates = mutableSetOf<TranscriptionWithChunk>()

            // Same topics
            if (chunkTopics.isNotEmpty()) {
                val topicMatches = otherChunks.filter { other ->
                    val otherTopics = parseTopicsList(other.topicsSummary)
                    otherTopics.any { it in chunkTopics }
                }
                candidates.addAll(topicMatches.take(MAX_CANDIDATES / 3))
            }

            // Same time of day (within 1 hour) on different dates
            val chunkHour = java.util.Calendar.getInstance().apply {
                timeInMillis = chunkStartTime
            }.get(java.util.Calendar.HOUR_OF_DAY)

            val timeMatches = otherChunks.filter { other ->
                other.date != chunkDate &&
                java.util.Calendar.getInstance().apply {
                    timeInMillis = other.startTime
                }.get(java.util.Calendar.HOUR_OF_DAY) in (chunkHour - 1)..(chunkHour + 1)
            }
            candidates.addAll(timeMatches.take(MAX_CANDIDATES / 3))

            // Recent chunks (potential continuations)
            val recentChunks = otherChunks
                .filter { it.date == chunkDate }
                .sortedBy { kotlin.math.abs(it.startTime - chunkStartTime) }
            candidates.addAll(recentChunks.take(MAX_CANDIDATES / 3))

            if (candidates.isEmpty()) return emptyList()

            // Try Claude for intelligent linking
            if (claudeAnalyzer.isAvailable()) {
                val claudeLinks = findLinksViaClaude(
                    chunkId, chunkText, chunkDate, chunkStartTime,
                    chunkTopics, chunkSpeakers, chunkActivity,
                    candidates.toList(),
                    bridgeBaseUrl
                )
                if (claudeLinks.isNotEmpty()) return claudeLinks
            }

            // Fallback: heuristic linking
            return findLinksHeuristic(
                chunkId, chunkTopics, chunkSpeakers, chunkDate,
                chunkStartTime, candidates.toList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find links: ${e.message}", e)
            return emptyList()
        }
    }

    private suspend fun findLinksViaClaude(
        chunkId: Long,
        chunkText: String,
        chunkDate: String,
        chunkStartTime: Long,
        chunkTopics: List<String>,
        chunkSpeakers: List<String>,
        chunkActivity: String?,
        candidates: List<TranscriptionWithChunk>,
        bridgeBaseUrl: String
    ): List<ConversationLinkEntity> {
        try {
            val url = URL("$bridgeBaseUrl/link")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 90_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val currentChunk = JSONObject().apply {
                put("chunkId", chunkId)
                put("text", chunkText.take(500))
                put("date", chunkDate)
                put("startTime", chunkStartTime)
                put("speakers", JSONArray(chunkSpeakers))
                put("topics", JSONArray(chunkTopics))
                if (chunkActivity != null) put("activity", chunkActivity)
            }

            val candidatesArr = JSONArray()
            candidates.take(MAX_CANDIDATES).forEach { c ->
                candidatesArr.put(JSONObject().apply {
                    put("chunkId", c.chunkId)
                    put("text", c.text.take(300))
                    put("date", c.date)
                    put("startTime", c.startTime)
                    put("speakers", JSONArray(emptyList<String>()))
                    put("topics", JSONArray(parseTopicsList(c.topicsSummary)))
                    if (c.activityType != null) put("activity", c.activityType)
                })
            }

            val body = JSONObject().apply {
                put("currentChunk", currentChunk)
                put("candidates", candidatesArr)
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
            val linksArr = responseObj.optJSONArray("links") ?: return emptyList()
            val now = System.currentTimeMillis()

            return (0 until linksArr.length()).mapNotNull { i ->
                val link = linksArr.getJSONObject(i)
                val strength = link.optDouble("strength", 0.5).toFloat()
                if (strength < 0.5f) return@mapNotNull null
                ConversationLinkEntity(
                    sourceChunkId = chunkId,
                    targetChunkId = link.getLong("targetChunkId"),
                    linkType = link.getString("linkType"),
                    description = link.optString("description", null),
                    strength = strength,
                    createdAt = now
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Claude link failed: ${e.message}")
            return emptyList()
        }
    }

    private fun findLinksHeuristic(
        chunkId: Long,
        chunkTopics: List<String>,
        chunkSpeakers: List<String>,
        chunkDate: String,
        chunkStartTime: Long,
        candidates: List<TranscriptionWithChunk>
    ): List<ConversationLinkEntity> {
        val now = System.currentTimeMillis()
        val links = mutableListOf<ConversationLinkEntity>()

        for (candidate in candidates) {
            val otherTopics = parseTopicsList(candidate.topicsSummary)
            val sharedTopics = chunkTopics.intersect(otherTopics.toSet())

            if (sharedTopics.isNotEmpty()) {
                links.add(ConversationLinkEntity(
                    sourceChunkId = chunkId,
                    targetChunkId = candidate.chunkId,
                    linkType = "same_topic",
                    description = "Shared topics: ${sharedTopics.joinToString()}",
                    strength = (sharedTopics.size.toFloat() / chunkTopics.size.coerceAtLeast(1)).coerceIn(0.5f, 1f),
                    createdAt = now
                ))
            }

            // Same time slot on different day
            if (candidate.date != chunkDate) {
                val chunkHour = java.util.Calendar.getInstance().apply {
                    timeInMillis = chunkStartTime
                }.get(java.util.Calendar.HOUR_OF_DAY)
                val otherHour = java.util.Calendar.getInstance().apply {
                    timeInMillis = candidate.startTime
                }.get(java.util.Calendar.HOUR_OF_DAY)

                if (kotlin.math.abs(chunkHour - otherHour) <= 1) {
                    links.add(ConversationLinkEntity(
                        sourceChunkId = chunkId,
                        targetChunkId = candidate.chunkId,
                        linkType = "same_time_slot",
                        description = "Similar time of day across different dates",
                        strength = 0.6f,
                        createdAt = now
                    ))
                }
            }

            // Continuation (same day, close in time)
            if (candidate.date == chunkDate) {
                val timeDiff = kotlin.math.abs(chunkStartTime - candidate.startTime)
                if (timeDiff < 600_000) { // within 10 minutes
                    links.add(ConversationLinkEntity(
                        sourceChunkId = chunkId,
                        targetChunkId = candidate.chunkId,
                        linkType = "continuation",
                        description = "Sequential conversation segment",
                        strength = 0.8f,
                        createdAt = now
                    ))
                }
            }
        }

        return links.take(10)
    }

    private fun parseTopicsList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }
}
