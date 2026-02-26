package com.dc.murmur.ai.nlp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bridges to the `claude` CLI (Claude Code) installed via Termux.
 * Uses the user's existing Pro/Max subscription — no extra API cost.
 *
 * Install on device:
 *   1. Install Termux
 *   2. pkg install nodejs
 *   3. npm install -g @anthropic-ai/claude-code
 *   4. claude login
 */
class ClaudeCodeAnalyzer {

    data class ClaudeAnalysis(
        val sentiment: String,      // "positive" | "negative" | "neutral"
        val sentimentScore: Float,  // 0.0 – 1.0
        val keywordsJson: String,   // JSON: {"summary":"...","tags":["..."]}
        val available: Boolean      // false = CLI not found or timed out
    )

    companion object {
        private const val TAG = "ClaudeCodeAnalyzer"
        private const val TIMEOUT_MS = 45_000L

        // Common locations for `claude` when installed via Termux npm
        private val CANDIDATE_PATHS = listOf(
            "/data/data/com.termux/files/usr/bin/claude",
            "/data/data/com.termux/files/home/.npm-global/bin/claude",
            "/data/data/com.termux/files/home/node_modules/.bin/claude",
            "/data/data/com.termux/files/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude",
            "/usr/local/bin/claude",
            "/usr/bin/claude"
        )

        private val PROMPT = """
Analyze this audio transcript. Respond in EXACTLY this format with no extra lines:

SENTIMENT: <positive|negative|neutral>
SCORE: <0.00-1.00>
TAGS: <up to 8 keywords, comma-separated>
SUMMARY: <one concise paragraph: main topics, key people/places mentioned, any action items>

Transcript:
%s
        """.trimIndent()
    }

    fun findClaudeBinary(): String? {
        // Check known paths first
        for (path in CANDIDATE_PATHS) {
            if (File(path).let { it.exists() && it.canExecute() }) return path
        }
        // Fallback: ask the shell
        return try {
            val proc = ProcessBuilder("which", "claude")
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readLine()?.trim()
                ?.takeIf { it.isNotBlank() && File(it).canExecute() }
        } catch (e: Exception) {
            null
        }
    }

    fun isAvailable(): Boolean = findClaudeBinary() != null

    suspend fun analyze(transcript: String): ClaudeAnalysis = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) {
            return@withContext unavailable("Empty transcript")
        }

        val claudePath = findClaudeBinary()
            ?: return@withContext unavailable("Claude Code CLI not found on device")

        val prompt = PROMPT.format(transcript.take(4000))

        val rawOutput = withTimeoutOrNull(TIMEOUT_MS) {
            runClaude(claudePath, prompt)
        }

        if (rawOutput == null) {
            Log.w(TAG, "Claude Code timed out after ${TIMEOUT_MS}ms")
            return@withContext ClaudeAnalysis(
                sentiment = "neutral",
                sentimentScore = 0.5f,
                keywordsJson = buildJson("Analysis timed out.", emptyList()),
                available = true
            )
        }

        parseOutput(rawOutput)
    }

    private fun runClaude(claudePath: String, prompt: String): String? {
        return try {
            val process = ProcessBuilder(claudePath, "-p", prompt)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "claude exited $exitCode: ${output.take(300)}")
                null
            } else {
                output
            }
        } catch (e: Exception) {
            Log.e(TAG, "ProcessBuilder failed: ${e.message}")
            null
        }
    }

    private fun parseOutput(output: String): ClaudeAnalysis {
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

        Log.d(TAG, "Parsed → sentiment=$sentiment score=$score tags=${tags.size} summary=${summary.take(60)}…")

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
