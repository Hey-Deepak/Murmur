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
data class HealthResponse(val status: String)

@Serializable
data class ErrorResponse(val error: String)

private const val DEFAULT_PORT = 8735
private const val TIMEOUT_SECONDS = 60L

private val PROMPT_TEMPLATE = """
Analyze this audio transcript. Respond in EXACTLY this format with no extra lines:

SENTIMENT: <positive|negative|neutral>
SCORE: <0.00-1.00>
TAGS: <up to 8 keywords, comma-separated>
SUMMARY: <one concise paragraph: main topics, key people/places mentioned, any action items>

Transcript:
%s
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

                val prompt = PROMPT_TEMPLATE.format(request.text.take(4000))

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

                val response = parseOutput(rawOutput)
                call.respond(response)
            }
        }
    }.start(wait = true)

    println("Claude Bridge running on http://127.0.0.1:$port")
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

    val sentiment = lines
        .firstOrNull { it.startsWith("SENTIMENT:") }
        ?.substringAfter("SENTIMENT:")?.trim()?.lowercase()
        .let { if (it in listOf("positive", "negative", "neutral")) it else "neutral" }!!

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
