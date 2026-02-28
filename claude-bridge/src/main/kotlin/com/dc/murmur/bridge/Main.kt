package com.dc.murmur.bridge

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

@Serializable
data class AnalyzeRequest(val text: String)

@Serializable
data class AnalyzeResponse(
    val sentiment: String,
    val score: Float,
    val tags: List<String>,
    val summary: String
)

@Serializable
data class RichAnalyzeResponse(
    val sentiment: String,
    val score: Float,
    val tags: List<String>,
    val summary: String,
    val activity: ActivityDetection,
    val speakers: List<SpeakerDetection>,
    val topics: List<TopicDetection>,
    val keyMoment: String? = null,
    val behavioralTags: List<String> = emptyList()
)

@Serializable
data class ActivityDetection(
    val type: String,
    val confidence: Float,
    val subActivity: String? = null
)

@Serializable
data class SpeakerDetection(
    val label: String,
    val speakingRatio: Float,
    val turnCount: Int,
    val role: String? = null,
    val emotionalState: String? = null
)

@Serializable
data class TopicDetection(
    val name: String,
    val relevance: Float,
    val category: String? = null,
    val keyPoints: List<String> = emptyList()
)

@Serializable
data class LinkRequest(
    val currentChunk: ChunkContext,
    val candidates: List<ChunkContext>
)

@Serializable
data class ChunkContext(
    val chunkId: Long,
    val text: String,
    val date: String,
    val startTime: Long,
    val speakers: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val activity: String? = null
)

@Serializable
data class LinkResponse(
    val links: List<DetectedLink>
)

@Serializable
data class DetectedLink(
    val targetChunkId: Long,
    val linkType: String,
    val description: String,
    val strength: Float
)

@Serializable
data class PredictRequest(
    val patterns: String,
    val currentDate: String,
    val currentTime: String
)

@Serializable
data class PredictResponse(
    val predictions: List<DetectedPrediction>
)

@Serializable
data class DetectedPrediction(
    val predictionType: String,
    val message: String,
    val confidence: Float,
    val basedOnDays: Int,
    val triggerTime: String? = null
)

@Serializable
data class DailyInsightRequest(
    val date: String,
    val activities: String,
    val topics: String,
    val people: String,
    val sentiments: String,
    val totalRecordedMs: Long,
    val totalChunks: Int
)

@Serializable
data class DailyInsightResponse(
    val timelineJson: String,
    val timeBreakdownJson: String,
    val peopleSummaryJson: String,
    val topTopics: String,
    val highlight: String?,
    val overallSentiment: String,
    val overallSentimentScore: Float
)

@Serializable
data class CleanupRequest(val text: String)

@Serializable
data class CleanupResponse(val text: String)

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class ErrorResponse(val error: String)

private const val DEFAULT_PORT = 8735
private const val TIMEOUT_SECONDS = 60L

private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val CLEANUP_PROMPT_TEMPLATE = """
Clean up this raw speech-to-text transcript. The speaker may use Hindi-English code-switching (Hinglish).

Rules:
- Add proper punctuation (periods, commas, question marks)
- Capitalize sentence beginnings and proper nouns
- Fix obvious transcription errors while preserving the original meaning
- Preserve Hindi words written in Latin script as-is (do not translate)
- Remove filler artifacts like repeated words or "[BLANK_AUDIO]"
- PRESERVE hesitations, self-corrections, and filler words (um, uh, hmm) — these are important for behavioral analysis
- Do NOT add any commentary — return ONLY the cleaned transcript text

Raw transcript:
%s
""".trimIndent()

private val RICH_ANALYZE_PROMPT_TEMPLATE = """
You are a behavioral intelligence analyst. Analyze this audio transcript to understand
the full context of what happened during this recording segment.

The speaker may use Hindi-English code-switching (Hinglish). Preserve Hindi words as-is.

Respond in EXACTLY this JSON format (valid JSON, no markdown, no comments):

{
  "sentiment": "<positive|negative|neutral|anxious|frustrated|confident|hesitant|excited>",
  "score": <0.00-1.00>,
  "tags": ["tag1", "tag2"],
  "summary": "One paragraph behavioral analysis...",
  "activity": {
    "type": "<eating|meeting|working|commuting|idle|phone_call|casual_chat|solo>",
    "confidence": <0.0-1.0>,
    "subActivity": "<optional: lunch, standup, debugging>"
  },
  "speakers": [
    {
      "label": "Speaker A",
      "speakingRatio": <0.0-1.0>,
      "turnCount": <int>,
      "role": "<dominant|listener|equal|null>",
      "emotionalState": "<calm|engaged|frustrated|excited|hesitant|null>"
    }
  ],
  "topics": [
    {
      "name": "topic name (lowercase, 1-3 words)",
      "relevance": <0.0-1.0>,
      "category": "<work|personal|health|finance|entertainment|education|null>",
      "keyPoints": ["point 1", "point 2"]
    }
  ],
  "keyMoment": "The most notable moment or shift in this segment, or null",
  "behavioralTags": ["rapid speech", "code-switching", "interrupting", "leading discussion"]
}

Rules:
- Detect distinct speakers from speech patterns (turn-taking, different perspectives)
- Activity type inferred from context clues (food mentions = eating, multiple speakers + agenda = meeting)
- Topics normalized to lowercase 1-3 word phrases
- Behavioral tags describe HOW the person speaks, not WHAT they say
- Respond ONLY with valid JSON, nothing else

Transcript:
%s
""".trimIndent()

private val LINK_PROMPT_TEMPLATE = """
You are analyzing conversation links between audio transcript chunks.
Given a current chunk and candidate related chunks, identify meaningful relationships.

Current chunk:
%s

Candidate chunks:
%s

For each meaningful link found, respond with a JSON array:
[
  {
    "targetChunkId": <id>,
    "linkType": "<same_person|same_topic|same_time_slot|cause_effect|continuation>",
    "description": "brief explanation of the link",
    "strength": <0.0-1.0>
  }
]

Only include links with strength >= 0.5. Respond ONLY with valid JSON array, nothing else.
If no meaningful links found, respond with: []
""".trimIndent()

private val PREDICT_PROMPT_TEMPLATE = """
You are a behavioral pattern analyst. Based on these observed patterns, generate predictions.

Patterns observed:
%s

Current date: %s
Current time: %s

Generate predictions as a JSON array:
[
  {
    "predictionType": "<routine|anomaly|relationship|habit>",
    "message": "Natural language prediction",
    "confidence": <0.0-1.0>,
    "basedOnDays": <int>,
    "triggerTime": "<HH:mm or null>"
  }
]

Only include predictions with confidence >= 0.5. Respond ONLY with valid JSON array, nothing else.
If no predictions possible, respond with: []
""".trimIndent()

private val DAILY_INSIGHT_PROMPT_TEMPLATE = """
You are a daily life intelligence analyst. Summarize this person's day based on their recorded audio data.

Date: %s
Total recorded: %d ms across %d chunks

Activities detected:
%s

Topics discussed:
%s

People encountered:
%s

Sentiment data:
%s

Generate a daily insight as JSON:
{
  "timelineJson": "[{\"time\":\"HH:mm\",\"activity\":\"...\",\"summary\":\"...\"}]",
  "timeBreakdownJson": "{\"working\":45,\"meeting\":30,\"idle\":25}",
  "peopleSummaryJson": "[{\"name\":\"...\",\"totalMs\":1234,\"sentiment\":\"...\"}]",
  "topTopics": "[\"topic1\",\"topic2\"]",
  "highlight": "Most notable moment of the day",
  "overallSentiment": "<positive|negative|neutral|mixed>",
  "overallSentimentScore": <0.0-1.0>
}

Respond ONLY with valid JSON, nothing else.
""".trimIndent()

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: DEFAULT_PORT

    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("/health") {
                call.respond(HealthResponse(status = "ok"))
            }
            post("/cleanup") {
                val request = try {
                    call.receive<CleanupRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON: ${e.message}"))
                    return@post
                }

                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Empty text"))
                    return@post
                }

                val prompt = CLEANUP_PROMPT_TEMPLATE.format(request.text.take(4000))

                val rawOutput = try {
                    runClaude(prompt)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude execution failed: ${e.message}")
                    )
                    return@post
                }

                if (rawOutput == null) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude returned no output or timed out")
                    )
                    return@post
                }

                call.respond(CleanupResponse(text = rawOutput.trim()))
            }
            post("/analyze") {
                val request = try {
                    call.receive<AnalyzeRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON: ${e.message}"))
                    return@post
                }

                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Empty text"))
                    return@post
                }

                val prompt = RICH_ANALYZE_PROMPT_TEMPLATE.format(request.text.take(4000))

                val rawOutput = try {
                    runClaude(prompt)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude execution failed: ${e.message}")
                    )
                    return@post
                }

                if (rawOutput == null) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude returned no output or timed out")
                    )
                    return@post
                }

                // Try parsing as rich JSON first, fall back to old format
                val richResponse = tryParseRich(rawOutput)
                if (richResponse != null) {
                    call.respond(richResponse)
                } else {
                    val response = parseOutput(rawOutput)
                    call.respond(response)
                }
            }
            post("/link") {
                val request = try {
                    call.receive<LinkRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON: ${e.message}"))
                    return@post
                }

                val currentChunkStr = formatChunkContext(request.currentChunk)
                val candidatesStr = request.candidates.joinToString("\n---\n") { formatChunkContext(it) }
                val prompt = LINK_PROMPT_TEMPLATE.format(currentChunkStr, candidatesStr)

                val rawOutput = try {
                    runClaude(prompt)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude execution failed: ${e.message}")
                    )
                    return@post
                }

                if (rawOutput == null) {
                    call.respond(LinkResponse(links = emptyList()))
                    return@post
                }

                val links = try {
                    val jsonStr = extractJson(rawOutput)
                    jsonParser.decodeFromString<List<DetectedLink>>(jsonStr)
                } catch (e: Exception) {
                    System.err.println("Failed to parse link response: ${e.message}")
                    emptyList()
                }

                call.respond(LinkResponse(links = links))
            }
            post("/predict") {
                val request = try {
                    call.receive<PredictRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON: ${e.message}"))
                    return@post
                }

                val prompt = PREDICT_PROMPT_TEMPLATE.format(
                    request.patterns,
                    request.currentDate,
                    request.currentTime
                )

                val rawOutput = try {
                    runClaude(prompt)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude execution failed: ${e.message}")
                    )
                    return@post
                }

                if (rawOutput == null) {
                    call.respond(PredictResponse(predictions = emptyList()))
                    return@post
                }

                val predictions = try {
                    val jsonStr = extractJson(rawOutput)
                    jsonParser.decodeFromString<List<DetectedPrediction>>(jsonStr)
                } catch (e: Exception) {
                    System.err.println("Failed to parse predict response: ${e.message}")
                    emptyList()
                }

                call.respond(PredictResponse(predictions = predictions))
            }
            post("/daily-insight") {
                val request = try {
                    call.receive<DailyInsightRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON: ${e.message}"))
                    return@post
                }

                val prompt = DAILY_INSIGHT_PROMPT_TEMPLATE.format(
                    request.date,
                    request.totalRecordedMs,
                    request.totalChunks,
                    request.activities.take(2000),
                    request.topics.take(1000),
                    request.people.take(1000),
                    request.sentiments.take(1000)
                )

                val rawOutput = try {
                    runClaude(prompt)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude execution failed: ${e.message}")
                    )
                    return@post
                }

                if (rawOutput == null) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Claude returned no output or timed out")
                    )
                    return@post
                }

                val response = try {
                    val jsonStr = extractJson(rawOutput)
                    jsonParser.decodeFromString<DailyInsightResponse>(jsonStr)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to parse daily insight: ${e.message}")
                    )
                    return@post
                }

                call.respond(response)
            }
        }
    }.start(wait = true)

    println("Claude Bridge running on http://127.0.0.1:$port")
}

private fun formatChunkContext(chunk: ChunkContext): String {
    return buildString {
        appendLine("Chunk #${chunk.chunkId} (${chunk.date} @ ${chunk.startTime})")
        if (chunk.activity != null) appendLine("Activity: ${chunk.activity}")
        if (chunk.speakers.isNotEmpty()) appendLine("Speakers: ${chunk.speakers.joinToString()}")
        if (chunk.topics.isNotEmpty()) appendLine("Topics: ${chunk.topics.joinToString()}")
        appendLine("Text: ${chunk.text.take(500)}")
    }
}

private fun extractJson(raw: String): String {
    val trimmed = raw.trim()
    // Try to find JSON object or array
    val startObj = trimmed.indexOf('{')
    val startArr = trimmed.indexOf('[')
    val start = when {
        startObj < 0 -> startArr
        startArr < 0 -> startObj
        else -> minOf(startObj, startArr)
    }
    if (start < 0) return trimmed

    val isArray = trimmed[start] == '['
    val endChar = if (isArray) ']' else '}'

    val end = trimmed.lastIndexOf(endChar)
    return if (end > start) trimmed.substring(start, end + 1) else trimmed
}

private fun tryParseRich(raw: String): RichAnalyzeResponse? {
    return try {
        val jsonStr = extractJson(raw)
        jsonParser.decodeFromString<RichAnalyzeResponse>(jsonStr)
    } catch (e: Exception) {
        null
    }
}

private fun runClaude(prompt: String): String? {
    val claudePath = findClaudeBinary() ?: error("claude binary not found in PATH")

    val process = ProcessBuilder(claudePath, "-p", "--")
        .redirectErrorStream(true)
        .start()

    // Write prompt via stdin to avoid argument-length limits and stdin-hang issues
    process.outputStream.bufferedWriter().use { it.write(prompt) }

    val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
    val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    if (!finished) {
        process.destroyForcibly()
        error("claude timed out after ${TIMEOUT_SECONDS}s")
    }

    if (process.exitValue() != 0) {
        System.err.println("claude exited ${process.exitValue()}: ${output.take(300)}")
        return null
    }

    return output
}

private fun findClaudeBinary(): String? {
    // Check common Termux paths
    val candidates = listOf(
        "/data/data/com.termux/files/usr/bin/claude",
        "/data/data/com.termux/files/home/.npm-global/bin/claude",
        "/data/data/com.termux/files/home/node_modules/.bin/claude",
        "/data/data/com.termux/files/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude",
        "/usr/local/bin/claude",
        "/usr/bin/claude"
    )

    for (path in candidates) {
        val f = java.io.File(path)
        if (f.exists() && f.canExecute()) return path
    }

    // Fallback: which
    return try {
        val proc = ProcessBuilder("which", "claude")
            .redirectErrorStream(true)
            .start()
        val line = proc.inputStream.bufferedReader().readLine()?.trim()
        proc.waitFor(5, TimeUnit.SECONDS)
        line?.takeIf { it.isNotBlank() && java.io.File(it).canExecute() }
    } catch (_: Exception) {
        null
    }
}

private fun parseOutput(output: String): AnalyzeResponse {
    val lines = output.lines()

    val validSentiments = listOf(
        "positive", "negative", "neutral", "anxious", "frustrated",
        "confident", "hesitant", "excited"
    )
    val sentiment = lines
        .firstOrNull { it.startsWith("SENTIMENT:") }
        ?.substringAfter("SENTIMENT:")?.trim()?.lowercase()
        .let { if (it in validSentiments) it else "neutral" }!!

    val score = lines
        .firstOrNull { it.startsWith("SCORE:") }
        ?.substringAfter("SCORE:")?.trim()
        ?.toFloatOrNull()?.coerceIn(0f, 1f)
        ?: if (sentiment == "neutral") 0.5f else 0.75f

    val tags = lines
        .firstOrNull { it.startsWith("TAGS:") }
        ?.substringAfter("TAGS:")?.trim()
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    val summaryStartIdx = lines.indexOfFirst { it.startsWith("SUMMARY:") }
    val summary = if (summaryStartIdx >= 0) {
        lines.subList(summaryStartIdx, lines.size)
            .joinToString(" ")
            .substringAfter("SUMMARY:")
            .trim()
    } else {
        output.trim().take(500)
    }

    return AnalyzeResponse(
        sentiment = sentiment,
        score = score,
        tags = tags,
        summary = summary
    )
}
